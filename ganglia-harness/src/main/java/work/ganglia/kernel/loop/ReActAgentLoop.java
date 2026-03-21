package work.ganglia.kernel.loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    this.pipeline = builder.pipeline != null ? builder.pipeline : new InterceptorPipeline();
  }

  public static Builder builder() {
    return new Builder();
  }

  private void publishObservation(String sessionId, ObservationType type, String content) {
    publishObservation(sessionId, type, content, null);
  }

  private void publishObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    logger.debug("Publishing observation: {} for session: {}", type, sessionId);
    if (dispatcher != null) {
      dispatcher.dispatch(sessionId, type, content, data);
    }
  }

  @Override
  public Future<String> run(String userInput, SessionContext initialContext, AgentSignal signal) {
    logger.debug(
        "Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
    sessionSignals.put(initialContext.sessionId(), signal);
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
                        // Fire and forget postTurn hook
                        pipeline
                            .executePostTurn(contextWithInput, Message.assistant(finalResponse))
                            .onFailure(err -> logger.warn("PostTurn hook failed", err));
                        return Future.succeededFuture(finalResponse);
                      });
            })
        .onComplete(v -> sessionSignals.remove(initialContext.sessionId()))
        .recover(
            err -> {
              if (!(err instanceof AgentInterruptException)) {
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
    }
  }

  @Override
  public Future<String> resume(
      String toolOutput, SessionContext initialContext, AgentSignal signal) {
    logger.debug("Resuming session: {}. Tool output received.", initialContext.sessionId());
    ToolCall pendingCall = findPendingToolCall(initialContext);
    if (pendingCall == null) {
      logger.error(
          "Resume failed: No pending tool call found for session: {}", initialContext.sessionId());
      return Future.failedFuture("No pending tool call found to resume.");
    }

    logger.debug("Matched resume output to toolCallId: {}", pendingCall.id());
    Message toolMessage = Message.tool(pendingCall.id(), pendingCall.toolName(), toolOutput);
    SessionContext context = sessionManager.addStep(initialContext, toolMessage);

    return sessionManager.persist(context).compose(v -> continueActingOrReason(context, signal));
  }

  private Future<String> continueActingOrReason(SessionContext context, AgentSignal signal) {
    Turn current = context.currentTurn();
    if (current != null) {
      List<ToolCall> pending = current.getPendingToolCalls();
      if (!pending.isEmpty()) {
        logger.debug("Continuing execution of {} pending tool calls.", pending.size());
        return act(pending, context, signal)
            .compose(
                contextAfterTools ->
                    sessionManager
                        .persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools, signal)))
            .recover(
                err -> {
                  if (err instanceof AgentInterruptException) {
                    return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                  }
                  return Future.failedFuture(err);
                });
      }
    }
    return runLoop(context, signal);
  }

  private ToolCall findPendingToolCall(SessionContext context) {
    Turn current = context.currentTurn();
    if (current == null) return null;
    return current.getPendingToolCalls().stream().findFirst().orElse(null);
  }

  private Future<String> runLoop(SessionContext currentContext, AgentSignal signal) {
    if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

    List<String> steeringMsgs = sessionManager.pollSteeringMessages(currentContext.sessionId());
    if (!steeringMsgs.isEmpty()) {
      logger.info("Injecting {} steering message(s) before reasoning.", steeringMsgs.size());
      for (String msg : steeringMsgs) {
        currentContext =
            sessionManager.addStep(
                currentContext, Message.user("System Interruption/Correction: " + msg));
      }
    }

    int iteration = currentContext.getIterationCount();
    int maxIterations = configProvider.getMaxIterations();
    if (iteration >= maxIterations) {
      logger.warn(
          "Iteration limit reached ({}) for session: {}",
          maxIterations,
          currentContext.sessionId());
      publishObservation(
          currentContext.sessionId(),
          ObservationType.ERROR,
          "Iteration limit reached (" + maxIterations + "). Safety abort.");
      return Future.succeededFuture("Max iterations reached without final answer.");
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
                          logger.info(
                              "Agent loop interrupted for session: {}. Waiting for user interaction.",
                              contextForReasoning.sessionId());
                          return Future.succeededFuture(
                              ((AgentInterruptException) err).getPrompt());
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
    if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

    int iteration = context.getIterationCount();
    publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

    return promptEngine
        .prepareRequest(context, iteration)
        .compose(
            promptRequest -> {
              if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

              ChatRequest chatRequest =
                  new ChatRequest(
                      promptRequest.messages(),
                      promptRequest.tools(),
                      promptRequest.options(),
                      signal);

              ExecutionContext execContext =
                  new ExecutionContext() {
                    @Override
                    public String sessionId() {
                      return context.sessionId();
                    }

                    @Override
                    public void emitStream(String chunk) {
                      publishObservation(
                          context.sessionId(), ObservationType.TOKEN_RECEIVED, chunk);
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
    if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

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
      publishObservation(currentContext.sessionId(), ObservationType.TURN_FINISHED, content);
      SessionContext finalContext =
          sessionManager.completeTurn(currentContext, Message.assistant(content));

      if (finalContext.currentTurn() != null) {
        String goal = finalContext.currentTurn().userMessage().content();
        MemoryEvent event =
            new MemoryEvent(
                MemoryEvent.EventType.TURN_COMPLETED,
                finalContext.sessionId(),
                goal,
                finalContext.currentTurn());
        vertx.eventBus().send(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(event));
      }

      return sessionManager.persist(finalContext).map(v -> content);
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
    if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

    List<String> steeringMsgs = sessionManager.pollSteeringMessages(currentContext.sessionId());
    if (!steeringMsgs.isEmpty()) {
      SessionContext updatedContext = currentContext;
      for (String msg : steeringMsgs) {
        updatedContext =
            sessionManager.addStep(
                updatedContext, Message.user("System Interruption/Correction: " + msg));
      }
      return Future.succeededFuture(updatedContext);
    }

    if (index >= tasks.size()) return Future.succeededFuture(currentContext);

    AgentTask task = tasks.get(index);
    Map<String, Object> toolData = new HashMap<>();
    toolData.put("toolCallId", task.id());
    if (task.getToolCall() != null && task.getToolCall().arguments() != null) {
      toolData.putAll(task.getToolCall().arguments());
    }
    publishObservation(
        currentContext.sessionId(), ObservationType.TOOL_STARTED, task.name(), toolData);

    final SessionContext originalContext = currentContext;
    ExecutionContext execContext =
        new ExecutionContext() {
          @Override
          public String sessionId() {
            return originalContext.sessionId();
          }

          @Override
          public void emitStream(String chunk) {
            if (dispatcher != null)
              dispatcher.dispatch(
                  originalContext.sessionId(),
                  ObservationType.TOOL_OUTPUT_STREAM,
                  chunk,
                  Map.of("toolCallId", task.id()));
          }

          @Override
          public void emitError(Throwable error) {
            if (dispatcher != null)
              dispatcher.dispatch(
                  originalContext.sessionId(),
                  ObservationType.ERROR,
                  error.getMessage(),
                  Map.of("toolCallId", task.id()));
          }
        };

    return pipeline
        .executePreToolExecute(task.getToolCall(), currentContext)
        .compose(
            hookedCall -> {
              // If hookedCall is rejected via a failed future, the recover block handles it
              return task.execute(originalContext, execContext)
                  .recover(
                      err ->
                          Future.succeededFuture(
                              new AgentTaskResult(
                                  AgentTaskResult.Status.EXCEPTION,
                                  "Execution error: " + err.getMessage(),
                                  null,
                                  null)))
                  .compose(
                      rawResult -> {
                        ToolInvokeResult toolResult =
                            new ToolInvokeResult(
                                rawResult.output(),
                                convertStatus(rawResult.status()),
                                null,
                                rawResult.modifiedContext(),
                                "",
                                rawResult.data());
                        return pipeline
                            .executePostToolExecute(hookedCall, toolResult, originalContext)
                            .map(
                                hookedResult ->
                                    new AgentTaskResult(
                                        convertStatusBack(hookedResult.status()),
                                        hookedResult.output(),
                                        hookedResult.modifiedContext(),
                                        hookedResult.data()));
                      });
            })
        .recover(
            err -> {
              // If preToolExecute blocked it or postToolExecute totally crashed
              return Future.succeededFuture(
                  new AgentTaskResult(
                      AgentTaskResult.Status.ERROR,
                      "Hook execution blocked/failed: " + err.getMessage(),
                      null,
                      null));
            })
        .compose(
            result -> {
              Map<String, Object> resData = Map.of("toolCallId", task.id());
              switch (result.status()) {
                case INTERRUPT -> {
                  Map<String, Object> interruptData = new java.util.HashMap<>(resData);
                  String askId = "ask-" + UUID.randomUUID().toString().substring(0, 8);
                  interruptData.put("askId", askId);
                  if (result.data() != null) {
                    interruptData.putAll(result.data());
                  }
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
                case ERROR, EXCEPTION ->
                    publishObservation(
                        originalContext.sessionId(),
                        ObservationType.TOOL_FINISHED,
                        "Error: " + result.output(),
                        resData);
                case SUCCESS ->
                    publishObservation(
                        originalContext.sessionId(),
                        ObservationType.TOOL_FINISHED,
                        result.output(),
                        resData);
              }

              Message toolMsg = Message.tool(task.id(), task.name(), result.output());
              SessionContext ctxToUse =
                  result.modifiedContext() != null ? result.modifiedContext() : originalContext;

              return faultTolerancePolicy
                  .handleFailure(ctxToUse, task, result)
                  .map(
                      handled ->
                          result.status() == AgentTaskResult.Status.SUCCESS
                              ? faultTolerancePolicy.onSuccess(handled, task)
                              : handled)
                  .compose(
                      finalCtx ->
                          sessionManager.addStep(finalCtx, toolMsg).persistWith(sessionManager))
                  .compose(nextCtx -> executeTasksSequentially(tasks, index + 1, nextCtx, signal));
            });
  }

  private ToolInvokeResult.Status convertStatus(AgentTaskResult.Status status) {
    return switch (status) {
      case SUCCESS -> ToolInvokeResult.Status.SUCCESS;
      case ERROR -> ToolInvokeResult.Status.ERROR;
      case EXCEPTION -> ToolInvokeResult.Status.EXCEPTION;
      case INTERRUPT -> ToolInvokeResult.Status.INTERRUPT;
    };
  }

  private AgentTaskResult.Status convertStatusBack(ToolInvokeResult.Status status) {
    return switch (status) {
      case SUCCESS -> AgentTaskResult.Status.SUCCESS;
      case ERROR -> AgentTaskResult.Status.ERROR;
      case EXCEPTION -> AgentTaskResult.Status.EXCEPTION;
      case INTERRUPT -> AgentTaskResult.Status.INTERRUPT;
    };
  }

  private Future<SessionContext> cancelRemainingTasks(
      List<AgentTask> tasks, int index, SessionContext currentContext) {
    if (index >= tasks.size()) return Future.succeededFuture(currentContext);
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

    public ReActAgentLoop build() {
      return new ReActAgentLoop(this);
    }
  }
}
