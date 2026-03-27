package work.ganglia.kernel.loop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.config.AgentConfigProvider;
import work.ganglia.infrastructure.external.llm.LLMException;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.kernel.hook.InterceptorPipeline;
import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;

/** Manages the ReAct reasoning loop. */
public class ReActAgentLoop implements AgentLoop {
  private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

  private final Vertx vertx;
  private final ObservationDispatcher dispatcher;
  private final SessionManager sessionManager;
  private final AgentConfigProvider configProvider;
  private final ContextOptimizer contextOptimizer;
  private final PromptEngine promptEngine;
  private final ModelGateway modelGateway;
  private final AgentTaskFactory taskFactory;
  private final FaultTolerancePolicy faultTolerancePolicy;
  private final InterceptorPipeline pipeline;

  private final ConcurrentHashMap<String, AgentSignal> sessionSignals = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, String> pendingAsks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> sessionStartTimes = new ConcurrentHashMap<>();

  private ReActAgentLoop(Builder builder) {
    this.vertx = builder.vertx;
    this.dispatcher = builder.dispatcher;
    this.sessionManager = builder.sessionManager;
    this.configProvider = builder.configProvider;
    this.contextOptimizer = builder.contextOptimizer;
    this.promptEngine = builder.promptEngine;
    this.modelGateway = builder.modelGateway;
    this.taskFactory = builder.taskFactory;
    this.faultTolerancePolicy = builder.faultTolerancePolicy;
    this.pipeline =
        builder.pipeline != null
            ? builder.pipeline
            : new InterceptorPipeline(this.dispatcher, builder.parentSessionId);
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Returns the dispatcher used by this loop. Used for testing observations. */
  public ObservationDispatcher getDispatcher() {
    return this.dispatcher;
  }

  private void publishObservation(String sessionId, ObservationType type, String content) {
    publishObservation(sessionId, type, content, null);
  }

  private void publishObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    AgentSignal signal = sessionSignals.get(sessionId);

    // If turn is finished (signal removed) or signal is aborted, ignore most events.
    // We only allow ERROR, TURN_FINISHED, and SYSTEM_EVENT to pass through.
    if (signal == null || signal.isAborted()) {
      if (type != ObservationType.ERROR
          && type != ObservationType.TURN_FINISHED
          && type != ObservationType.SYSTEM_EVENT
          && type != ObservationType.SESSION_ENDED) {
        return;
      }
    }

    logger.debug("Publishing observation: {} for session: {}", type, sessionId);
    if (dispatcher != null) {
      dispatcher.dispatch(sessionId, type, content, data);
    }
  }

  private void publishSessionEnded(String sessionId) {
    Long startTime = sessionStartTimes.remove(sessionId);
    long durationMs = startTime != null ? System.currentTimeMillis() - startTime : 0L;
    Map<String, Object> data = new HashMap<>();
    data.put("durationMs", durationMs);
    if (dispatcher != null) {
      dispatcher.dispatch(sessionId, ObservationType.SESSION_ENDED, null, data);
    }
    // Publish SESSION_CLOSED memory event
    if (vertx != null) {
      MemoryEvent memEvent =
          new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null);
      vertx.eventBus().publish(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(memEvent));
    }
  }

  @Override
  public Future<String> run(String userInput, SessionContext initialContext, AgentSignal signal) {
    sessionSignals.put(initialContext.sessionId(), signal);
    sessionStartTimes.put(initialContext.sessionId(), System.currentTimeMillis());
    publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, userInput);

    return pipeline
        .executePreTurn(initialContext, userInput)
        .compose(
            hookedContext -> {
              Message userMessage = Message.user(userInput);
              SessionContext contextWithInput =
                  sessionManager.startTurn(hookedContext, userMessage);

              return sessionManager
                  .persist(contextWithInput)
                  .compose(v -> runLoop(contextWithInput, signal))
                  .compose(
                      finalResponse -> {
                        pipeline
                            .executePostTurn(initialContext, Message.assistant(finalResponse))
                            .onFailure(err -> logger.warn("PostTurn hook failed", err));
                        return Future.succeededFuture(finalResponse);
                      });
            })
        .onComplete(
            v -> {
              sessionSignals.remove(initialContext.sessionId());
              publishSessionEnded(initialContext.sessionId());
            })
        .recover(
            err -> {
              if (err instanceof AgentAbortedException) {
                Map<String, Object> abortData = new HashMap<>();
                abortData.put("reason", "abort_propagated");
                if (dispatcher != null) {
                  dispatcher.dispatch(
                      initialContext.sessionId(), ObservationType.SESSION_ABORTED, null, abortData);
                }
              } else if (!(err instanceof AgentInterruptException)) {
                publishObservation(
                    initialContext.sessionId(), ObservationType.ERROR, err.getMessage());
              }
              return Future.failedFuture(err);
            });
  }

  public void stop(String sessionId) {
    AgentSignal signal = sessionSignals.get(sessionId);
    if (signal != null) {
      logger.info("Aborting session via stop request: {}", sessionId);
      signal.abort();
      Map<String, Object> abortData = new HashMap<>();
      abortData.put("reason", "user_stop");
      if (dispatcher != null) {
        dispatcher.dispatch(sessionId, ObservationType.SESSION_ABORTED, null, abortData);
      }
    }
  }

  @Override
  public Future<String> resume(
      String askId, String toolOutput, SessionContext initialContext, AgentSignal signal) {
    logger.debug("Resuming session: {} with askId: {}", initialContext.sessionId(), askId);
    sessionSignals.put(initialContext.sessionId(), signal);
    sessionStartTimes.putIfAbsent(initialContext.sessionId(), System.currentTimeMillis());

    String toolCallId = askId != null ? pendingAsks.remove(askId) : null;
    ToolCall pendingCall = findPendingToolCall(initialContext, toolCallId);
    if (pendingCall == null) {
      sessionSignals.remove(initialContext.sessionId());
      return Future.failedFuture("No pending tool call found to resume.");
    }

    Message toolMessage = Message.tool(pendingCall.id(), pendingCall.toolName(), toolOutput);
    SessionContext context = sessionManager.addStep(initialContext, toolMessage);

    return sessionManager
        .persist(context)
        .compose(v -> continueActingOrReason(context, signal))
        .compose(
            finalResponse -> {
              pipeline
                  .executePostTurn(initialContext, Message.assistant(finalResponse))
                  .onFailure(err -> logger.warn("PostTurn hook failed", err));
              return Future.succeededFuture(finalResponse);
            })
        .onComplete(
            v -> {
              sessionSignals.remove(initialContext.sessionId());
              publishSessionEnded(initialContext.sessionId());
            })
        .recover(
            err -> {
              if (err instanceof AgentAbortedException) {
                Map<String, Object> abortData = new HashMap<>();
                abortData.put("reason", "abort_propagated");
                if (dispatcher != null) {
                  dispatcher.dispatch(
                      initialContext.sessionId(), ObservationType.SESSION_ABORTED, null, abortData);
                }
              } else if (!(err instanceof AgentInterruptException)) {
                publishObservation(
                    initialContext.sessionId(), ObservationType.ERROR, err.getMessage());
              }
              return Future.failedFuture(err);
            });
  }

  private Future<String> continueActingOrReason(SessionContext context, AgentSignal signal) {
    Turn current = context.currentTurn();
    if (current != null) {
      List<ToolCall> pending = current.getPendingToolCalls();
      if (!pending.isEmpty()) {
        return act(pending, context, signal)
            .compose(
                contextAfterTools ->
                    sessionManager
                        .persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools, signal)));
      }
    }
    return runLoop(context, signal);
  }

  private ToolCall findPendingToolCall(SessionContext context, String toolCallId) {
    Turn current = context.currentTurn();
    if (current == null) {
      return null;
    }
    List<ToolCall> pending = current.getPendingToolCalls();
    if (toolCallId != null) {
      return pending.stream().filter(tc -> tc.id().equals(toolCallId)).findFirst().orElse(null);
    }
    return pending.stream().findFirst().orElse(null);
  }

  private Future<String> runLoop(SessionContext currentContext, AgentSignal signal) {
    if (signal.isAborted()) {
      return Future.failedFuture(new AgentAbortedException());
    }

    int iteration = currentContext.getIterationCount();
    int maxIterations = configProvider.getMaxIterations();
    if (iteration >= maxIterations) {
      publishObservation(
          currentContext.sessionId(),
          ObservationType.ERROR,
          "Iteration limit reached (" + maxIterations + "). Safety abort.");
      return Future.succeededFuture("Max iterations reached.");
    }

    return contextOptimizer
        .optimizeIfNeeded(currentContext)
        .compose(
            optimizedContext -> {
              final SessionContext contextForReasoning = optimizedContext;
              return reason(contextForReasoning, signal)
                  .compose(response -> handleDecision(response, contextForReasoning, signal))
                  .recover(
                      err -> {
                        if (err instanceof AgentInterruptException) {
                          return Future.succeededFuture(
                              ((AgentInterruptException) err).getPrompt());
                        }
                        if (err instanceof AgentAbortedException) {
                          logger.debug(
                              "Agent loop aborted for session: {}",
                              contextForReasoning.sessionId());
                          return Future.failedFuture(err);
                        }
                        logger.error(
                            "Error in agent loop for session: {}",
                            contextForReasoning.sessionId(),
                            err);
                        Map<String, Object> errorData = new HashMap<>();
                        if (err instanceof LLMException llmErr) {
                          llmErr.errorCode().ifPresent(code -> errorData.put("errorCode", code));
                          llmErr
                              .httpStatusCode()
                              .ifPresent(status -> errorData.put("httpStatusCode", status));
                          llmErr.requestId().ifPresent(id -> errorData.put("requestId", id));
                        }
                        publishObservation(
                            contextForReasoning.sessionId(),
                            ObservationType.ERROR,
                            err.getMessage(),
                            errorData);
                        return Future.failedFuture(err);
                      });
            });
  }

  private Future<ModelResponse> reason(SessionContext context, AgentSignal signal) {
    if (signal.isAborted()) {
      return Future.failedFuture(new AgentAbortedException());
    }

    int iteration = context.getIterationCount();
    publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

    return promptEngine
        .prepareRequest(context, iteration)
        .compose(
            promptRequest -> {
              if (signal.isAborted()) {
                return Future.failedFuture(new AgentAbortedException());
              }

              ChatRequest chatRequest =
                  new ChatRequest(
                      promptRequest.messages(),
                      promptRequest.tools(),
                      promptRequest.options(),
                      signal);

              // Publish observation of prepared request metadata
              Map<String, Object> reqData = new HashMap<>();
              reqData.put("messageCount", chatRequest.messages().size());
              reqData.put("toolCount", chatRequest.tools().size());
              reqData.put("model", chatRequest.options().modelName());
              publishObservation(
                  context.sessionId(), ObservationType.REQUEST_PREPARED, null, reqData);

              ExecutionContext execContext =
                  new ExecutionContext() {
                    @Override
                    public String sessionId() {
                      return context.sessionId();
                    }

                    @Override
                    public void emitStream(String chunk) {
                      if (!signal.isAborted()) {
                        publishObservation(
                            context.sessionId(), ObservationType.TOKEN_RECEIVED, chunk);
                      }
                    }

                    @Override
                    public void emitError(Throwable error) {
                      publishObservation(
                          context.sessionId(), ObservationType.ERROR, error.getMessage());
                    }
                  };

              if (chatRequest.options().stream()) {
                return modelGateway.chatStream(chatRequest, execContext);
              } else {
                return modelGateway.chat(chatRequest);
              }
            });
  }

  private Future<String> handleDecision(
      ModelResponse response, SessionContext currentContext, AgentSignal signal) {
    if (signal.isAborted()) {
      return Future.failedFuture(new AgentAbortedException());
    }

    String content = response.content();
    List<ToolCall> toolCalls = response.toolCalls();

    if (response.usage() != null && dispatcher instanceof AgentLoopObserver observer) {
      observer.onUsageRecorded(currentContext.sessionId(), response.usage());
    }

    if (!toolCalls.isEmpty()) {
      if (content != null && !content.isBlank()) {
        publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);
      }
      Message assistantMessage = Message.assistant(content, toolCalls);
      SessionContext nextContext = sessionManager.addStep(currentContext, assistantMessage);

      return sessionManager
          .persist(nextContext)
          .compose(v -> act(toolCalls, nextContext, signal))
          .compose(
              contextAfterTools ->
                  sessionManager
                      .persist(contextAfterTools)
                      .compose(v -> runLoop(contextAfterTools, signal)));
    } else {
      SessionContext finalContext =
          sessionManager.completeTurn(currentContext, Message.assistant(content));

      publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);

      return sessionManager
          .persist(finalContext)
          .map(
              v -> {
                publishObservation(
                    finalContext.sessionId(), ObservationType.TURN_FINISHED, content);
                // Publish TURN_COMPLETED memory event
                if (vertx != null) {
                  Turn completedTurn = finalContext.currentTurn();
                  MemoryEvent memEvent =
                      new MemoryEvent(
                          MemoryEvent.EventType.TURN_COMPLETED,
                          finalContext.sessionId(),
                          null,
                          completedTurn);
                  vertx
                      .eventBus()
                      .publish(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(memEvent));
                }
                return content;
              });
    }
  }

  private Future<SessionContext> act(
      List<ToolCall> toolCalls, SessionContext context, AgentSignal signal) {
    List<AgentTask> tasks =
        toolCalls.stream().map(call -> taskFactory.create(call, context)).toList();
    return executeTasksSequentially(tasks, 0, context, signal);
  }

  private Future<SessionContext> executeTasksSequentially(
      List<AgentTask> tasks, int index, SessionContext currentContext, AgentSignal signal) {
    if (signal.isAborted()) {
      return Future.failedFuture(new AgentAbortedException());
    }
    if (index >= tasks.size()) {
      return Future.succeededFuture(currentContext);
    }

    AgentTask task = tasks.get(index);
    Map<String, Object> toolData = new HashMap<>();
    toolData.put("toolCallId", task.id());
    if (task.getToolCall() != null && task.getToolCall().arguments() != null) {
      toolData.putAll(task.getToolCall().arguments());
    }

    final SessionContext originalContext = currentContext;
    ExecutionContext execContext =
        new ExecutionContext() {
          @Override
          public String sessionId() {
            return originalContext.sessionId();
          }

          @Override
          public void emitStream(String chunk) {
            if (dispatcher != null) {
              dispatcher.dispatch(
                  originalContext.sessionId(),
                  ObservationType.TOOL_OUTPUT_STREAM,
                  chunk,
                  Map.of("toolCallId", task.id()));
            }
          }

          @Override
          public void emitError(Throwable error) {
            if (dispatcher != null) {
              dispatcher.dispatch(
                  originalContext.sessionId(),
                  ObservationType.ERROR,
                  error.getMessage(),
                  Map.of("toolCallId", task.id()));
            }
          }
        };

    final long toolStartMs = System.currentTimeMillis();
    return pipeline
        .executePreToolExecute(task.getToolCall(), originalContext)
        .compose(call -> task.execute(originalContext, execContext))
        .timeout(configProvider.getToolTimeoutMs(), TimeUnit.MILLISECONDS)
        .recover(
            err ->
                Future.succeededFuture(
                    new AgentTaskResult(
                        AgentTaskResult.Status.EXCEPTION,
                        "Error: " + err.getMessage(),
                        null,
                        null)))
        .compose(
            taskResult -> {
              // Convert AgentTaskResult back to ToolInvokeResult for interceptors
              ToolInvokeResult.Status toolStatus =
                  ToolInvokeResult.Status.valueOf(taskResult.status().name());
              ToolInvokeResult invokeResult =
                  new ToolInvokeResult(
                      taskResult.output(),
                      toolStatus,
                      null, // AgentTaskResult doesn't carry ToolErrorResult details
                      taskResult.modifiedContext(),
                      null, // AgentTaskResult doesn't carry diff
                      taskResult.data());

              // Execute post-tool interceptors
              return pipeline
                  .executePostToolExecute(
                      task.getToolCall(), invokeResult, originalContext, toolStartMs)
                  .map(
                      interceptedResult ->
                          new AgentTaskResult(
                              AgentTaskResult.Status.valueOf(interceptedResult.status().name()),
                              interceptedResult.output(),
                              interceptedResult.modifiedContext(),
                              interceptedResult.data()));
            })
        .compose(
            result -> {
              Map<String, Object> resData = Map.of("toolCallId", task.id());

              if (result.status() == AgentTaskResult.Status.INTERRUPT) {
                String askId = "ask-" + UUID.randomUUID().toString().substring(0, 8);
                pendingAsks.put(askId, task.id());
                Map<String, Object> interruptData = new HashMap<>(resData);
                if (result.data() != null) {
                  interruptData.putAll(result.data());
                }
                interruptData.put("askId", askId);

                publishObservation(
                    originalContext.sessionId(),
                    ObservationType.USER_INTERACTION_REQUIRED,
                    "Interrupted: " + result.output(),
                    interruptData);

                Message interruptMsg =
                    Message.tool(task.id(), task.name(), "INTERRUPTED: " + result.output());
                SessionContext interruptedContext =
                    sessionManager.addStep(originalContext, interruptMsg);

                return cancelRemainingTasks(tasks, index + 1, interruptedContext)
                    .compose(finalCtx -> sessionManager.persist(finalCtx))
                    .compose(
                        v ->
                            Future.failedFuture(
                                new AgentInterruptException(result.output(), askId)));
              }

              Message toolMsg = Message.tool(task.id(), task.name(), result.output());
              SessionContext ctxToUse =
                  result.modifiedContext() != null ? result.modifiedContext() : originalContext;

              // Apply fault tolerance policy
              if (result.status() == AgentTaskResult.Status.ERROR
                  || result.status() == AgentTaskResult.Status.EXCEPTION) {
                return faultTolerancePolicy
                    .handleFailure(ctxToUse, task, result)
                    .compose(
                        policyCtx ->
                            sessionManager
                                .addStep(policyCtx, toolMsg)
                                .persistWith(sessionManager)
                                .compose(
                                    nextCtx ->
                                        executeTasksSequentially(
                                            tasks, index + 1, nextCtx, signal)));
              }

              SessionContext successCtx = faultTolerancePolicy.onSuccess(ctxToUse, task);
              return sessionManager
                  .addStep(successCtx, toolMsg)
                  .persistWith(sessionManager)
                  .compose(nextCtx -> executeTasksSequentially(tasks, index + 1, nextCtx, signal));
            });
  }

  private Future<SessionContext> cancelRemainingTasks(
      List<AgentTask> tasks, int index, SessionContext currentContext) {
    if (index >= tasks.size()) {
      return Future.succeededFuture(currentContext);
    }
    AgentTask task = tasks.get(index);
    Message cancelMsg =
        Message.tool(task.id(), task.name(), "CANCELLED: Previous task interrupted the flow.");
    return cancelRemainingTasks(tasks, index + 1, currentContext.addStep(cancelMsg));
  }

  public static class Builder {
    private Vertx vertx;
    private ObservationDispatcher dispatcher;
    private SessionManager sessionManager;
    private AgentConfigProvider configProvider;
    private ContextOptimizer contextOptimizer;
    private PromptEngine promptEngine;
    private ModelGateway modelGateway;
    private AgentTaskFactory taskFactory;
    private FaultTolerancePolicy faultTolerancePolicy;
    private InterceptorPipeline pipeline;
    private String parentSessionId;

    private Builder() {}

    public Builder vertx(Vertx vertx) {
      this.vertx = vertx;
      return this;
    }

    public Builder dispatcher(ObservationDispatcher dispatcher) {
      this.dispatcher = dispatcher;
      return this;
    }

    public Builder sessionManager(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
      return this;
    }

    public Builder configProvider(AgentConfigProvider configProvider) {
      this.configProvider = configProvider;
      return this;
    }

    public Builder contextOptimizer(ContextOptimizer contextOptimizer) {
      this.contextOptimizer = contextOptimizer;
      return this;
    }

    public Builder promptEngine(PromptEngine promptEngine) {
      this.promptEngine = promptEngine;
      return this;
    }

    public Builder modelGateway(ModelGateway modelGateway) {
      this.modelGateway = modelGateway;
      return this;
    }

    public Builder taskFactory(AgentTaskFactory taskFactory) {
      this.taskFactory = taskFactory;
      return this;
    }

    public Builder faultTolerancePolicy(FaultTolerancePolicy faultTolerancePolicy) {
      this.faultTolerancePolicy = faultTolerancePolicy;
      return this;
    }

    public Builder pipeline(InterceptorPipeline pipeline) {
      this.pipeline = pipeline;
      return this;
    }

    public Builder parentSessionId(String parentSessionId) {
      this.parentSessionId = parentSessionId;
      return this;
    }

    public ReActAgentLoop build() {
      return new ReActAgentLoop(this);
    }
  }
}
