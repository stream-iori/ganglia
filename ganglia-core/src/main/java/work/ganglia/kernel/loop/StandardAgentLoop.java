package work.ganglia.kernel.loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.config.ConfigManager;
import work.ganglia.infrastructure.external.llm.LLMException;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.chat.*;
import work.ganglia.port.external.llm.*;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.state.*;;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.kernel.task.SchedulableResult;
import work.ganglia.kernel.task.Schedulable;
import work.ganglia.kernel.task.SchedulableFactory;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.infrastructure.internal.state.ContextOptimizer;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class StandardAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(StandardAgentLoop.class);

    private final ModelGateway model;
    private final SchedulableFactory scheduleableFactory;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final ConfigManager configManager;
    private final Vertx vertx;
    private final ContextOptimizer contextOptimizer;
    private final FaultTolerancePolicy faultTolerancePolicy;
    private final List<AgentLoopObserver> observers;
    private final Map<String, AgentSignal> sessionSignals = new java.util.concurrent.ConcurrentHashMap<>();

    public StandardAgentLoop(Vertx vertx, ModelGateway model, SchedulableFactory scheduleableFactory, SessionManager sessionManager,
                             PromptEngine promptEngine, ConfigManager configManager, ContextOptimizer contextOptimizer,
                             FaultTolerancePolicy faultTolerancePolicy, List<AgentLoopObserver> observers) {
        this.vertx = vertx;
        this.model = model;
        this.scheduleableFactory = scheduleableFactory;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.contextOptimizer = contextOptimizer;
        this.faultTolerancePolicy = faultTolerancePolicy;
        this.observers = observers == null ? Collections.emptyList() : new ArrayList<>(observers);
    }

    private void publishObservation(String sessionId, ObservationType type, String content) {
        publishObservation(sessionId, type, content, null);
    }

    private void publishObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        logger.debug("Publishing observation: {} for session: {}", type, sessionId);
        observers.forEach(o -> o.onObservation(sessionId, type, content, data));
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext, AgentSignal signal) {
        logger.debug("Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
        sessionSignals.put(initialContext.sessionId(), signal);
        publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, userInput);
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = sessionManager.startTurn(initialContext, userMessage);
        return sessionManager.persist(context)
            .compose(v -> runLoop(context, signal))
            .onComplete(v -> sessionSignals.remove(initialContext.sessionId()))
            .recover(err -> {
                // Ensure top-level errors are also published
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
        // Find the last pending tool call from context
        ToolCall pendingCall = findPendingToolCall(initialContext);
        if (pendingCall == null) {
            logger.error("Resume failed: No pending tool call found for session: {}", initialContext.sessionId());
            return Future.failedFuture("No pending tool call found to resume.");
        }

        logger.debug("Matched resume output to toolCallId: {}", pendingCall.id());
        Message toolMessage = Message.tool(pendingCall.id(), pendingCall.toolName(), toolOutput);
        SessionContext context = sessionManager.addStep(initialContext, toolMessage);

        return sessionManager
            .persist(context)
            .compose(v -> {
                // Check if there are MORE pending tool calls in the same set that need execution
                // before we go back to reasoning.
                return continueActingOrReason(context, signal);
            });
    }

    private Future<String> continueActingOrReason(SessionContext context, AgentSignal signal) {
        Turn current = context.currentTurn();
        if (current != null) {
            List<ToolCall> pending = current.getPendingToolCalls();

            if (!pending.isEmpty()) {
                logger.debug("Continuing execution of {} pending tool calls.", pending.size());
                return act(pending, context, signal)
                    .compose(contextAfterTools ->
                        sessionManager
                            .persist(contextAfterTools)
                            .compose(v -> runLoop(contextAfterTools, signal))
                    )
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

    /**
     * Identifies a tool call that has been requested by the Assistant but has not yet
     * received a corresponding result from the Tool system. This is crucial for
     * resuming a session after an interrupt (e.g., user confirmation).
     *
     * @param context The current session context containing the turn history.
     * @return The ID of the pending tool call, or null if all calls are accounted for.
     */
    private ToolCall findPendingToolCall(SessionContext context) {
        Turn current = context.currentTurn();
        if (current == null) return null;

        return current.getPendingToolCalls().stream()
            .findFirst()
            .orElse(null);
    }

    private Future<String> runLoop(SessionContext currentContext, AgentSignal signal) {
        if (signal.isAborted()) {
            return Future.failedFuture(new AgentAbortedException());
        }

        // --- 1. Process Steering Messages (Outer Loop Check) ---
        List<String> steeringMsgs = sessionManager.pollSteeringMessages(currentContext.sessionId());
        if (!steeringMsgs.isEmpty()) {
            logger.info("Injecting {} steering message(s) before reasoning.", steeringMsgs.size());
            for (String msg : steeringMsgs) {
                currentContext = sessionManager.addStep(currentContext, Message.user("System Interruption/Correction: " + msg));
            }
        }

        int iteration = currentContext.getIterationCount();
        int maxIterations = configManager.getMaxIterations();
        if (iteration >= maxIterations) {
            logger.warn("Iteration limit reached ({}) for session: {}", maxIterations, currentContext.sessionId());
            publishObservation(currentContext.sessionId(), ObservationType.ERROR, "Iteration limit reached (" + maxIterations + "). Safety abort.");
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        // --- Proactive Context Compression Check ---
        return contextOptimizer.optimizeIfNeeded(currentContext)
            .compose(optimizedContext -> {
                logger.debug("Loop iteration: {} for session: {}", iteration, optimizedContext.sessionId());

                // Pass context cleanly to reason block, capture effectively final references correctly
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
                        if (err instanceof LLMException) {
                            LLMException llmErr = (LLMException) err;
                            llmErr.errorCode().ifPresent(code -> errorData.put("errorCode", code));
                            llmErr.httpStatusCode().ifPresent(status -> errorData.put("httpStatusCode", status));
                            llmErr.requestId().ifPresent(id -> errorData.put("requestId", id));
                        }

                        publishObservation(contextForReasoning.sessionId(), ObservationType.ERROR, err.getMessage(), errorData);
                        return Future.failedFuture(err);
                    });
            });
    }

    // --- Core Logic Steps ---

    private Future<ModelResponse> reason(SessionContext context, AgentSignal signal) {
        if (signal.isAborted()) {
            return Future.failedFuture(new AgentAbortedException());
        }

        int iteration = context.getIterationCount();
        publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

        return promptEngine.prepareRequest(context, iteration)
            .compose(request -> {
                if (signal.isAborted()) {
                    return Future.failedFuture(new AgentAbortedException());
                }
                logger.debug("Calling model: {} with history size: {}, stream: {}",
                    request.options().modelName(), request.messages().size(), request.options().stream());
                if (request.options().stream()) {
                    return model.chatStream(request.messages(), request.tools(), request.options(), context.sessionId());
                } else {
                    return model.chat(request.messages(), request.tools(), request.options());
                }
            });
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, AgentSignal signal) {
        if (signal.isAborted()) {
            return Future.failedFuture(new AgentAbortedException());
        }

        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

        logger.debug("Model response content: {}", content);
        logger.debug("Model requested {} tool call(s).", toolCalls.size());

        // Record usage asynchronously
        if (response.usage() != null) {
            observers.forEach(o -> o.onUsageRecorded(currentContext.sessionId(), response.usage()));
        }

        if (!toolCalls.isEmpty()) {
            // Content with tool calls is reasoning/thought
            if (content != null && !content.isBlank()) {
                publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);
            }

            // Decision: Act (Execute ALL Tools)
            // Persist the Assistant's intent (Tool Calls) BEFORE executing, so we can resume if interrupted.
            Message assistantMessage = Message.assistant(content, toolCalls);
            SessionContext nextContext = sessionManager.addStep(currentContext, assistantMessage);

            return sessionManager.persist(nextContext)
                .compose(v -> act(toolCalls, nextContext, signal))
                .compose(contextAfterTools ->
                    // Loop: Recurse
                    sessionManager.persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools, signal))
                );
        } else {
            logger.debug("No tool calls. Turn complete.");
            // Content without tool calls is the final answer
            // In non-streaming mode, we directly go to TURN_FINISHED
            publishObservation(currentContext.sessionId(), ObservationType.TURN_FINISHED, content);
            
            // Decision: Finish
            SessionContext finalContext = sessionManager.completeTurn(currentContext, Message.assistant(content));

            // Async background reflection for Daily Journal via EventBus
            if (finalContext.currentTurn() != null) {
                String goal = finalContext.currentTurn().userMessage().content();
                MemoryEvent event = new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, finalContext.sessionId(), goal, finalContext.currentTurn());
                vertx.eventBus().send(Constants.ADDRESS_MEMORY_EVENT, JsonObject.mapFrom(event));
            }

            return sessionManager
                .persist(finalContext)
                .map(v -> content);
        }
    }

    private Future<SessionContext> act(List<ToolCall> toolCalls, SessionContext context, AgentSignal signal) {
        // Transform ToolCalls to Schedulables
        List<Schedulable> scheduleables = toolCalls.stream()
            .map(call -> scheduleableFactory.create(call, context))
            .toList();

        return executeSchedulablesSequentially(scheduleables, 0, context, signal);
    }

    private Future<SessionContext> executeSchedulablesSequentially(List<Schedulable> scheduleables, int index, SessionContext currentContext, AgentSignal signal) {
        if (signal.isAborted()) {
            return Future.failedFuture(new AgentAbortedException());
        }

        // --- 2. Process Steering Messages (Inner Loop Check) ---
        List<String> steeringMsgs = sessionManager.pollSteeringMessages(currentContext.sessionId());
        if (!steeringMsgs.isEmpty()) {
            logger.info("Steering message received during tool execution. Aborting remaining {} tasks.", scheduleables.size() - index);
            SessionContext updatedContext = currentContext;
            for (String msg : steeringMsgs) {
                updatedContext = sessionManager.addStep(updatedContext, Message.user("System Interruption/Correction: " + msg));
            }
            // Return early with the updated context containing the steering messages
            return Future.succeededFuture(updatedContext);
        }

        if (index >= scheduleables.size()) {
            return Future.succeededFuture(currentContext);
        }

        Schedulable task = scheduleables.get(index);
        logger.debug("Executing task [{} of {}]: {} ({})", index + 1, scheduleables.size(), task.name(), task.id());
        
        Map<String, Object> startData = new java.util.HashMap<>();
        startData.put("toolCallId", task.id());
        publishObservation(currentContext.sessionId(), ObservationType.TOOL_STARTED, task.name(), startData);

        final SessionContext originalContextForTask = currentContext;

        return task.execute(originalContextForTask)
            .recover(err -> {
                logger.error("Task {} threw an unhandled exception: {}", task.name(), err.getMessage());
                return Future.succeededFuture(new SchedulableResult(SchedulableResult.Status.EXCEPTION, "Execution error: " + err.getMessage(), null));
            })
            .compose(scheduleResult -> {
                Map<String, Object> resultData = new java.util.HashMap<>();
                resultData.put("toolCallId", task.id());
                
                switch (scheduleResult.status()) {
                    case INTERRUPT -> {
                        logger.debug("Task {} requested interrupt.", task.name());
                        publishObservation(originalContextForTask.sessionId(), ObservationType.TOOL_FINISHED, "Interrupted: " + scheduleResult.output(), resultData);
                        
                        // IMPORTANT: Even if interrupted, we MUST provide a response for THIS tool call
                        // so that the conversation history remains valid for the next turn.
                        Message interruptMsg = Message.tool(task.id(), task.name(), "INTERRUPTED: " + scheduleResult.output());
                        SessionContext interruptedContext = sessionManager.addStep(originalContextForTask, interruptMsg);

                        // Also satisfy OpenAI by responding to ALL REMAINING tool calls.
                        return cancelRemainingTasks(scheduleables, index + 1, interruptedContext)
                            .compose(finalContext -> sessionManager.persist(finalContext))
                            .compose(v -> Future.failedFuture(new AgentInterruptException(scheduleResult.output())));
                    }
                    case ERROR, EXCEPTION -> {
                        logger.warn("Task {} failed with status {}: {}", task.name(), scheduleResult.status(), scheduleResult.output());
                        publishObservation(originalContextForTask.sessionId(), ObservationType.TOOL_FINISHED, "Error: " + scheduleResult.output(), resultData);
                    }
                    case SUCCESS -> {
                        logger.debug("Task {} executed successfully.", task.name());
                        publishObservation(originalContextForTask.sessionId(), ObservationType.TOOL_FINISHED, scheduleResult.output(), resultData);
                    }
                }

                Message toolMsg = Message.tool(task.id(), task.name(), scheduleResult.output());
                SessionContext contextToUse = scheduleResult.modifiedContext() != null ? scheduleResult.modifiedContext() : originalContextForTask;

                // --- Robustness: Fault Tolerance Strategy ---
                return faultTolerancePolicy.handleFailure(contextToUse, task, scheduleResult)
                    .map(handledContext -> {
                        if (scheduleResult.status() == SchedulableResult.Status.SUCCESS) {
                            return faultTolerancePolicy.onSuccess(handledContext, task);
                        }
                        return handledContext;
                    })
                    .compose(finalContextToUse -> {
                        SessionContext nextContext = sessionManager.addStep(finalContextToUse, toolMsg);
                        return sessionManager.persist(nextContext).map(v -> nextContext);
                    })
                    .compose(nextContext -> executeSchedulablesSequentially(scheduleables, index + 1, nextContext, signal));
            });
    }

    private Future<SessionContext> cancelRemainingTasks(List<Schedulable> scheduleables, int index, SessionContext currentContext) {
        if (index >= scheduleables.size()) {
            return Future.succeededFuture(currentContext);
        }
        Schedulable task = scheduleables.get(index);
        Message cancelMsg = Message.tool(task.id(), task.name(), "CANCELLED: Previous task interrupted the flow.");
        SessionContext nextContext = currentContext.addStep(cancelMsg);
        return cancelRemainingTasks(scheduleables, index + 1, nextContext);
    }
}
