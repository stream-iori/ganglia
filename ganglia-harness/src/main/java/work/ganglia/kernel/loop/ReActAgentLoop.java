package work.ganglia.kernel.loop;

import java.util.ArrayDeque;
import java.util.Deque;
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
import work.ganglia.infrastructure.internal.state.ContextPressure;
import work.ganglia.infrastructure.internal.state.ContextPressureMonitor;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
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
import work.ganglia.port.internal.memory.ContextCompressor;
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
  private final ContextCompressor contextCompressor;
  private final ContextPressureMonitor pressureMonitor;

  private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PendingAsk> pendingAsks = new ConcurrentHashMap<>();

  /** Tracks a pending ask with its tool call ID, creation time, and owning session. */
  private record PendingAsk(String toolCallId, long createdAtMs, String sessionId) {}

  private static final long PENDING_ASK_TTL_MS = 30 * 60 * 1000L; // 30 minutes

  /** Consolidated per-session state to prevent map sprawl and ensure atomic cleanup. */
  private static class SessionState {
    volatile AgentSignal signal;
    long startTimeMs;
    final Deque<String> spanStack = new ArrayDeque<>();
    final java.util.concurrent.atomic.AtomicInteger activeTurns =
        new java.util.concurrent.atomic.AtomicInteger(0);
    String sessionSpanId;
  }

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
    this.contextCompressor = builder.contextCompressor;
    this.pressureMonitor = builder.pressureMonitor;
    this.pipeline = builder.pipeline != null ? builder.pipeline : new InterceptorPipeline();
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
    SessionState state = sessions.get(sessionId);
    AgentSignal signal = state != null ? state.signal : null;

    // If turn is finished (signal removed) or signal is aborted, ignore most events.
    if (signal == null || signal.isAborted()) {
      if (type != ObservationType.ERROR
          && type != ObservationType.TURN_FINISHED
          && type != ObservationType.SYSTEM_EVENT
          && type != ObservationType.SESSION_ENDED
          && type != ObservationType.SESSION_ABORTED) {
        return;
      }
    }

    String spanId = null;
    String parentSpanId = null;
    if (state != null && !state.spanStack.isEmpty()) {
      var it = state.spanStack.iterator();
      spanId = it.next();
      if (it.hasNext()) {
        parentSpanId = it.next();
      }
    }

    logger.debug("Publishing observation: {} for session: {}", type, sessionId);
    if (dispatcher != null) {
      dispatcher.dispatch(sessionId, type, content, data, spanId, parentSpanId);
    }
  }

  private void startSpan(String sessionId, String spanId) {
    SessionState state = sessions.get(sessionId);
    if (state != null) {
      state.spanStack.push(spanId);
    }
  }

  private void endSpan(String sessionId) {
    SessionState state = sessions.get(sessionId);
    if (state != null && !state.spanStack.isEmpty()) {
      state.spanStack.pop();
    }
  }

  private String getCurrentSpanId(String sessionId) {
    SessionState state = sessions.get(sessionId);
    return (state != null && !state.spanStack.isEmpty()) ? state.spanStack.peek() : null;
  }

  private void publishSessionEnded(String sessionId) {
    SessionState state = sessions.get(sessionId);
    if (state == null) {
      return;
    }

    // Only publish SESSION_ENDED when the last active turn for this session completes
    if (state.activeTurns.decrementAndGet() > 0) {
      return; // Other turns still active
    }

    // Pop session-level span if present
    if (state.sessionSpanId != null) {
      state.sessionSpanId = null;
      endSpan(sessionId); // pop session span
    }

    long durationMs = state.startTimeMs > 0 ? System.currentTimeMillis() - state.startTimeMs : 0L;
    Map<String, Object> data = new HashMap<>();
    data.put("durationMs", durationMs);
    publishObservation(sessionId, ObservationType.SESSION_ENDED, null, data);
    // Publish SESSION_CLOSED memory event
    if (vertx != null) {
      MemoryEvent memEvent =
          new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null);
      vertx.eventBus().publish(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(memEvent));
    }
    cleanupSession(sessionId);
  }

  /** Removes all per-session state from concurrent maps to prevent memory leaks. */
  private void cleanupSession(String sessionId) {
    sessions.remove(sessionId);
    pendingAsks.values().removeIf(pa -> sessionId.equals(pa.sessionId()));
  }

  @Override
  public Future<String> run(String userInput, SessionContext initialContext, AgentSignal signal) {
    SessionState state =
        sessions.computeIfAbsent(initialContext.sessionId(), k -> new SessionState());
    state.signal = signal;
    if (state.startTimeMs == 0) {
      state.startTimeMs = System.currentTimeMillis();
    }
    state.activeTurns.incrementAndGet();

    // If this is the very first turn of the session, create session-level span and publish
    // SESSION_STARTED
    if (initialContext.previousTurns().isEmpty() && initialContext.currentTurn() == null) {
      String sessionSpanId = "session-" + UUID.randomUUID().toString().substring(0, 8);
      state.sessionSpanId = sessionSpanId;
      startSpan(initialContext.sessionId(), sessionSpanId);

      Map<String, Object> sessionStartData = new HashMap<>();
      sessionStartData.put("firstPrompt", userInput != null ? userInput : "");
      if (initialContext.modelOptions() != null
          && initialContext.modelOptions().modelName() != null) {
        sessionStartData.put("model", initialContext.modelOptions().modelName());
      }
      publishObservation(
          initialContext.sessionId(), ObservationType.SESSION_STARTED, userInput, sessionStartData);
    }

    String turnSpanId = "turn-" + UUID.randomUUID().toString().substring(0, 8);
    startSpan(initialContext.sessionId(), turnSpanId);

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
                        publishObservation(
                            initialContext.sessionId(),
                            ObservationType.TURN_FINISHED,
                            finalResponse);
                        endSpan(initialContext.sessionId());
                        return Future.succeededFuture(finalResponse);
                      });
            })
        .recover(
            err -> {
              if (err instanceof AgentAbortedException) {
                Map<String, Object> abortData = new HashMap<>();
                abortData.put("reason", "abort_propagated");
                publishObservation(
                    initialContext.sessionId(), ObservationType.SESSION_ABORTED, null, abortData);
              } else if (!(err instanceof AgentInterruptException)) {
                publishObservation(
                    initialContext.sessionId(), ObservationType.ERROR, err.getMessage());
              }
              return Future.failedFuture(err);
            })
        .onComplete(
            v -> {
              if (v.failed()) {
                endSpan(initialContext.sessionId());
              }
              publishSessionEnded(initialContext.sessionId());
            });
  }

  public void stop(String sessionId) {
    SessionState state = sessions.get(sessionId);
    if (state != null && state.signal != null) {
      logger.info("Aborting session via stop request: {}", sessionId);
      // abort() is thread-safe (AtomicBoolean), can be called from any thread
      state.signal.abort();
      // Schedule publishObservation to run on Vert.x event loop thread
      // to ensure thread-safe access to spanStack
      if (vertx != null) {
        vertx.runOnContext(
            v -> {
              // Re-check state in case session was cleaned up
              if (sessions.containsKey(sessionId)) {
                Map<String, Object> abortData = new HashMap<>();
                abortData.put("reason", "user_stop");
                publishObservation(sessionId, ObservationType.SESSION_ABORTED, null, abortData);
              }
            });
      }
    }
  }

  @Override
  public Future<String> resume(
      String askId, String toolOutput, SessionContext initialContext, AgentSignal signal) {
    logger.debug("Resuming session: {} with askId: {}", initialContext.sessionId(), askId);
    SessionState state =
        sessions.computeIfAbsent(initialContext.sessionId(), k -> new SessionState());
    state.signal = signal;
    if (state.startTimeMs == 0) {
      state.startTimeMs = System.currentTimeMillis();
    }
    state.activeTurns.incrementAndGet();

    String turnSpanId = "turn-" + UUID.randomUUID().toString().substring(0, 8);
    startSpan(initialContext.sessionId(), turnSpanId);
    publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, toolOutput);

    String toolCallId = null;
    if (askId != null) {
      PendingAsk pending = pendingAsks.remove(askId);
      if (pending != null) {
        if (System.currentTimeMillis() - pending.createdAtMs() > PENDING_ASK_TTL_MS) {
          endSpan(initialContext.sessionId());
          return Future.failedFuture("Pending ask expired (older than 30 minutes).");
        }
        toolCallId = pending.toolCallId();
      }
    }
    ToolCall pendingCall = findPendingToolCall(initialContext, toolCallId);
    if (pendingCall == null) {
      endSpan(initialContext.sessionId());
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
              publishObservation(
                  initialContext.sessionId(), ObservationType.TURN_FINISHED, finalResponse);
              endSpan(initialContext.sessionId());
              return Future.succeededFuture(finalResponse);
            })
        .recover(
            err -> {
              if (err instanceof AgentAbortedException) {
                Map<String, Object> abortData = new HashMap<>();
                abortData.put("reason", "abort_propagated");
                publishObservation(
                    initialContext.sessionId(), ObservationType.SESSION_ABORTED, null, abortData);
              } else if (!(err instanceof AgentInterruptException)) {
                publishObservation(
                    initialContext.sessionId(), ObservationType.ERROR, err.getMessage());
              }
              return Future.failedFuture(err);
            })
        .onComplete(
            v -> {
              if (v.failed()) {
                endSpan(initialContext.sessionId());
              }
              publishSessionEnded(initialContext.sessionId());
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

    // Check context pressure and notify if level changed
    if (pressureMonitor != null) {
      ContextPressure pressure = pressureMonitor.evaluateAndNotify(currentContext);
      if (pressure.isBlocking()) {
        logger.warn(
            "Context at BLOCKING level ({}%), compression will be forced",
            (int) (pressure.percentUsed() * 100));
      }
    }

    // Time-based trigger: clear old tool results if cache likely expired
    SessionContext contextForOptimization = currentContext;
    if (contextOptimizer instanceof DefaultContextOptimizer defaultOptimizer
        && !currentContext.previousTurns().isEmpty()) {
      long cacheExpiryMs = configProvider.getCacheExpiryMs();
      contextForOptimization =
          defaultOptimizer.compactExpiredToolResults(currentContext, cacheExpiryMs);
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
        .optimizeIfNeeded(contextForOptimization, getCurrentSpanId(currentContext.sessionId()))
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
                          errorData.put("errorType", "llm_error");
                          int status = llmErr.httpStatusCode().orElse(0);
                          errorData.put(
                              "recoverable", status == 429 || (status >= 500 && status < 600));
                        } else if (err instanceof AgentAbortedException) {
                          errorData.put("errorType", "abort");
                          errorData.put("recoverable", false);
                        } else {
                          errorData.put("errorType", "system_error");
                          errorData.put("recoverable", false);
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

    String modelSpanId = "model-" + UUID.randomUUID().toString().substring(0, 8);
    startSpan(context.sessionId(), modelSpanId);

    int iteration = context.getIterationCount();
    publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

    return promptEngine
        .prepareRequest(context, iteration)
        .compose(
            promptRequest -> {
              if (signal.isAborted()) {
                endSpan(context.sessionId());
                return Future.failedFuture(new AgentAbortedException());
              }

              ChatRequest chatRequest =
                  new ChatRequest(
                      context.sessionId(),
                      promptRequest.messages(),
                      promptRequest.tools(),
                      promptRequest.options(),
                      signal,
                      modelSpanId);

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
                    public String spanId() {
                      return modelSpanId;
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

              Future<ModelResponse> responseFuture;
              if (chatRequest.options().stream()) {
                responseFuture = modelGateway.chatStream(chatRequest, execContext);
              } else {
                responseFuture = modelGateway.chat(chatRequest);
              }

              return responseFuture
                  .onSuccess(r -> endSpan(context.sessionId()))
                  .onFailure(e -> endSpan(context.sessionId()));
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
          .compose(
              v -> {
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

                // Async key facts extraction for running summary
                if (contextCompressor != null && finalContext.currentTurn() != null) {
                  String existing = finalContext.getRunningSummary();
                  contextCompressor
                      .extractKeyFacts(finalContext.currentTurn(), existing)
                      .onSuccess(
                          summary -> {
                            SessionContext updated = finalContext.withRunningSummary(summary);
                            sessionManager.persist(updated);
                          })
                      .onFailure(
                          err ->
                              logger.warn(
                                  "Failed to extract key facts for session {}",
                                  finalContext.sessionId(),
                                  err));
                }

                return Future.succeededFuture(content);
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
    String toolSpanId = "tool-" + UUID.randomUUID().toString().substring(0, 8);
    startSpan(currentContext.sessionId(), toolSpanId);

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
          public String spanId() {
            return toolSpanId;
          }

          @Override
          public void emitStream(String chunk) {
            if (dispatcher != null) {
              SessionState st = sessions.get(originalContext.sessionId());
              String currentSpan = null;
              String parentSpan = null;
              if (st != null && !st.spanStack.isEmpty()) {
                var iter = st.spanStack.iterator();
                currentSpan = iter.next();
                if (iter.hasNext()) parentSpan = iter.next();
              }
              dispatcher.dispatch(
                  originalContext.sessionId(),
                  ObservationType.TOOL_OUTPUT_STREAM,
                  chunk,
                  Map.of("toolCallId", task.id()),
                  currentSpan,
                  parentSpan);
            }
          }

          @Override
          public void emitError(Throwable error) {
            publishObservation(
                originalContext.sessionId(),
                ObservationType.ERROR,
                error.getMessage(),
                Map.of("toolCallId", task.id()));
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
                      taskResult.metadata());

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
                              interceptedResult.metadata()));
            })
        .compose(
            result -> {
              Map<String, Object> resData = new HashMap<>();
              resData.put("toolCallId", task.id());
              resData.put("durationMs", System.currentTimeMillis() - toolStartMs);

              publishObservation(
                  originalContext.sessionId(),
                  ObservationType.TOOL_FINISHED,
                  result.status().name(),
                  resData);
              endSpan(originalContext.sessionId());

              if (result.status() == AgentTaskResult.Status.INTERRUPT) {
                String askId = "ask-" + UUID.randomUUID().toString().substring(0, 8);
                pendingAsks.put(
                    askId,
                    new PendingAsk(
                        task.id(), System.currentTimeMillis(), originalContext.sessionId()));
                Map<String, Object> interruptData = new HashMap<>(resData);
                if (result.metadata() != null) {
                  interruptData.putAll(result.metadata());
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

              boolean outputCapped =
                  result.metadata() != null
                      && Boolean.TRUE.equals(
                          result.metadata().get(ToolInvokeResult.KEY_OUTPUT_CAPPED));
              Message toolMsg =
                  outputCapped
                      ? Message.toolCapped(task.id(), task.name(), result.output())
                      : Message.tool(task.id(), task.name(), result.output());
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

  /** Returns the number of active sessions tracked. Visible for testing leak detection. */
  int getActiveSessionCount() {
    return sessions.size();
  }

  /** Returns the number of pending ask entries. Visible for testing leak detection. */
  int getPendingAskCount() {
    return pendingAsks.size();
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
    private ContextCompressor contextCompressor;
    private ContextPressureMonitor pressureMonitor;
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

    public Builder contextCompressor(ContextCompressor contextCompressor) {
      this.contextCompressor = contextCompressor;
      return this;
    }

    public Builder pressureMonitor(ContextPressureMonitor pressureMonitor) {
      this.pressureMonitor = pressureMonitor;
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
