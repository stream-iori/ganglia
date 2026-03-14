package work.ganglia.kernel.loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.config.AgentConfigProvider;
import work.ganglia.infrastructure.external.llm.LLMException;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.util.Constants;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SRP: Manages the ReAct reasoning loop.
 * Simplified using AgentEnv for dependency management.
 */
public class ReActAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final AgentEnv env;
    private final ConcurrentHashMap<String, AgentSignal> sessionSignals = new ConcurrentHashMap<>();

    public ReActAgentLoop(AgentEnv env) {
        this.env = env;
    }

    private void publishObservation(String sessionId, ObservationType type, String content) {
        publishObservation(sessionId, type, content, null);
    }

    private void publishObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        logger.debug("Publishing observation: {} for session: {}", type, sessionId);
        if (env.dispatcher() != null) {
            env.dispatcher().dispatch(sessionId, type, content, data);
        }
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext, AgentSignal signal) {
        logger.debug("Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
        sessionSignals.put(initialContext.sessionId(), signal);
        publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, userInput);
        
        Message userMessage = Message.user(userInput);
        SessionContext context = env.sessionManager().startTurn(initialContext, userMessage);
        
        return env.sessionManager().persist(context)
            .compose(v -> runLoop(context, signal))
            .onComplete(v -> sessionSignals.remove(initialContext.sessionId()))
            .recover(err -> {
                if (!(err instanceof AgentInterruptException)) {
                    publishObservation(initialContext.sessionId(), ObservationType.ERROR, err.getMessage());
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
    public Future<String> resume(String toolOutput, SessionContext initialContext, AgentSignal signal) {
        logger.debug("Resuming session: {}. Tool output received.", initialContext.sessionId());
        ToolCall pendingCall = findPendingToolCall(initialContext);
        if (pendingCall == null) {
            logger.error("Resume failed: No pending tool call found for session: {}", initialContext.sessionId());
            return Future.failedFuture("No pending tool call found to resume.");
        }

        logger.debug("Matched resume output to toolCallId: {}", pendingCall.id());
        Message toolMessage = Message.tool(pendingCall.id(), pendingCall.toolName(), toolOutput);
        SessionContext context = env.sessionManager().addStep(initialContext, toolMessage);

        return env.sessionManager().persist(context)
            .compose(v -> continueActingOrReason(context, signal));
    }

    private Future<String> continueActingOrReason(SessionContext context, AgentSignal signal) {
        Turn current = context.currentTurn();
        if (current != null) {
            List<ToolCall> pending = current.getPendingToolCalls();
            if (!pending.isEmpty()) {
                logger.debug("Continuing execution of {} pending tool calls.", pending.size());
                return act(pending, context, signal)
                    .compose(contextAfterTools -> env.sessionManager().persist(contextAfterTools).compose(v -> runLoop(contextAfterTools, signal)))
                    .recover(err -> {
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

        List<String> steeringMsgs = env.sessionManager().pollSteeringMessages(currentContext.sessionId());
        if (!steeringMsgs.isEmpty()) {
            logger.info("Injecting {} steering message(s) before reasoning.", steeringMsgs.size());
            for (String msg : steeringMsgs) {
                currentContext = env.sessionManager().addStep(currentContext, Message.user("System Interruption/Correction: " + msg));
            }
        }

        int iteration = currentContext.getIterationCount();
        int maxIterations = env.configProvider().getMaxIterations();
        if (iteration >= maxIterations) {
            logger.warn("Iteration limit reached ({}) for session: {}", maxIterations, currentContext.sessionId());
            publishObservation(currentContext.sessionId(), ObservationType.ERROR, "Iteration limit reached (" + maxIterations + "). Safety abort.");
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        return env.contextOptimizer().optimizeIfNeeded(currentContext)
            .compose(optimizedContext -> {
                final SessionContext contextForReasoning = optimizedContext;
                return reason(contextForReasoning, signal)
                    .compose(response -> handleDecision(response, contextForReasoning, signal))
                    .recover(err -> {
                        if (err instanceof AgentInterruptException) {
                            logger.info("Agent loop interrupted for session: {}. Waiting for user interaction.", contextForReasoning.sessionId());
                            return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                        }
                        logger.error("Error in agent loop for session: {}", contextForReasoning.sessionId(), err);
                        Map<String, Object> errorData = new HashMap<>();
                        if (err instanceof LLMException llmErr) {
                            llmErr.errorCode().ifPresent(code -> errorData.put("errorCode", code));
                            llmErr.httpStatusCode().ifPresent(status -> errorData.put("httpStatusCode", status));
                            llmErr.requestId().ifPresent(id -> errorData.put("requestId", id));
                        }
                        publishObservation(contextForReasoning.sessionId(), ObservationType.ERROR, err.getMessage(), errorData);
                        return Future.failedFuture(err);
                    });
            });
    }

    private Future<ModelResponse> reason(SessionContext context, AgentSignal signal) {
        if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

        int iteration = context.getIterationCount();
        publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

        return env.promptEngine().prepareRequest(context, iteration)
            .compose(promptRequest -> {
                if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());
                
                ChatRequest chatRequest = new ChatRequest(promptRequest.messages(), promptRequest.tools(), promptRequest.options(), signal);
                
                ExecutionContext execContext = new ExecutionContext() {
                    @Override public String sessionId() { return context.sessionId(); }
                    @Override public void emitStream(String chunk) { publishObservation(context.sessionId(), ObservationType.TOKEN_RECEIVED, chunk); }
                    @Override public void emitError(Throwable error) { publishObservation(context.sessionId(), ObservationType.ERROR, error.getMessage()); }
                };

                if (chatRequest.options().stream()) {
                    return env.modelGateway().chatStream(chatRequest, execContext);
                } else {
                    return env.modelGateway().chat(chatRequest);
                }
            });
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, AgentSignal signal) {
        if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

        if (response.usage() != null && env.dispatcher() instanceof AgentLoopObserver observer) {
            observer.onUsageRecorded(currentContext.sessionId(), response.usage());
        }

        if (!toolCalls.isEmpty()) {
            if (content != null && !content.isBlank()) {
                publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);
            }
            Message assistantMessage = Message.assistant(content, toolCalls);
            SessionContext nextContext = env.sessionManager().addStep(currentContext, assistantMessage);

            return env.sessionManager().persist(nextContext)
                .compose(v -> act(toolCalls, nextContext, signal))
                .compose(contextAfterTools -> env.sessionManager().persist(contextAfterTools).compose(v -> runLoop(contextAfterTools, signal)));
        } else {
            publishObservation(currentContext.sessionId(), ObservationType.TURN_FINISHED, content);
            SessionContext finalContext = env.sessionManager().completeTurn(currentContext, Message.assistant(content));

            if (finalContext.currentTurn() != null) {
                String goal = finalContext.currentTurn().userMessage().content();
                MemoryEvent event = new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, finalContext.sessionId(), goal, finalContext.currentTurn());
                env.vertx().eventBus().send(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(event));
            }

            return env.sessionManager().persist(finalContext).map(v -> content);
        }
    }

    private Future<SessionContext> act(List<ToolCall> toolCalls, SessionContext context, AgentSignal signal) {
        List<AgentTask> tasks = toolCalls.stream().map(call -> env.taskFactory().create(call, context)).toList();
        return executeTasksSequentially(tasks, 0, context, signal);
    }

    private Future<SessionContext> executeTasksSequentially(List<AgentTask> tasks, int index, SessionContext currentContext, AgentSignal signal) {
        if (signal.isAborted()) return Future.failedFuture(new AgentAbortedException());

        List<String> steeringMsgs = env.sessionManager().pollSteeringMessages(currentContext.sessionId());
        if (!steeringMsgs.isEmpty()) {
            SessionContext updatedContext = currentContext;
            for (String msg : steeringMsgs) {
                updatedContext = env.sessionManager().addStep(updatedContext, Message.user("System Interruption/Correction: " + msg));
            }
            return Future.succeededFuture(updatedContext);
        }

        if (index >= tasks.size()) return Future.succeededFuture(currentContext);

        AgentTask task = tasks.get(index);
        publishObservation(currentContext.sessionId(), ObservationType.TOOL_STARTED, task.name(), Map.of("toolCallId", task.id()));

        final SessionContext originalContext = currentContext;
        ExecutionContext execContext = new ExecutionContext() {
            @Override public String sessionId() { return originalContext.sessionId(); }
            @Override public void emitStream(String chunk) { 
                if (env.dispatcher() != null) env.dispatcher().dispatch(originalContext.sessionId(), ObservationType.TOOL_OUTPUT_STREAM, chunk, Map.of("toolCallId", task.id())); 
            }
            @Override public void emitError(Throwable error) {
                if (env.dispatcher() != null) env.dispatcher().dispatch(originalContext.sessionId(), ObservationType.ERROR, error.getMessage(), Map.of("toolCallId", task.id()));
            }
        };

        return task.execute(originalContext, execContext)
            .recover(err -> Future.succeededFuture(new AgentTaskResult(AgentTaskResult.Status.EXCEPTION, "Execution error: " + err.getMessage(), null)))
            .compose(result -> {
                Map<String, Object> resData = Map.of("toolCallId", task.id());
                switch (result.status()) {
                    case INTERRUPT -> {
                        publishObservation(originalContext.sessionId(), ObservationType.TOOL_FINISHED, "Interrupted: " + result.output(), resData);
                        Message interruptMsg = Message.tool(task.id(), task.name(), "INTERRUPTED: " + result.output());
                        SessionContext interruptedContext = env.sessionManager().addStep(originalContext, interruptMsg);
                        return cancelRemainingTasks(tasks, index + 1, interruptedContext)
                            .compose(finalCtx -> env.sessionManager().persist(finalCtx))
                            .compose(v -> Future.failedFuture(new AgentInterruptException(result.output())));
                    }
                    case ERROR, EXCEPTION -> publishObservation(originalContext.sessionId(), ObservationType.TOOL_FINISHED, "Error: " + result.output(), resData);
                    case SUCCESS -> publishObservation(originalContext.sessionId(), ObservationType.TOOL_FINISHED, result.output(), resData);
                }

                Message toolMsg = Message.tool(task.id(), task.name(), result.output());
                SessionContext ctxToUse = result.modifiedContext() != null ? result.modifiedContext() : originalContext;

                return env.faultTolerancePolicy().handleFailure(ctxToUse, task, result)
                    .map(handled -> result.status() == AgentTaskResult.Status.SUCCESS ? env.faultTolerancePolicy().onSuccess(handled, task) : handled)
                    .compose(finalCtx -> env.sessionManager().addStep(finalCtx, toolMsg).persistWith(env.sessionManager()))
                    .compose(nextCtx -> executeTasksSequentially(tasks, index + 1, nextCtx, signal));
            });
    }

    private Future<SessionContext> cancelRemainingTasks(List<AgentTask> tasks, int index, SessionContext currentContext) {
        if (index >= tasks.size()) return Future.succeededFuture(currentContext);
        AgentTask task = tasks.get(index);
        Message cancelMsg = Message.tool(task.id(), task.name(), "CANCELLED: Previous task interrupted the flow.");
        return cancelRemainingTasks(tasks, index + 1, currentContext.addStep(cancelMsg));
    }
}
