package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.llm.LLMException;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ReActAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final ModelGateway model;
    private final ToolExecutor toolExecutor;
    private final SessionManager sessionManager;
    private final PromptEngine promptEngine;
    private final int maxIterations;
    private final Vertx vertx;
    private final me.stream.ganglia.memory.ContextCompressor compressor;
    private final me.stream.ganglia.memory.DailyRecordManager dailyRecordManager;
    private final me.stream.ganglia.memory.TokenCounter tokenCounter = new me.stream.ganglia.memory.TokenCounter();

    public ReActAgentLoop(Vertx vertx, ModelGateway model, ToolExecutor toolExecutor, SessionManager sessionManager, 
                          PromptEngine promptEngine, int maxIterations, 
                          me.stream.ganglia.memory.ContextCompressor compressor, 
                          me.stream.ganglia.memory.DailyRecordManager dailyRecordManager) {
        this.vertx = vertx;
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.maxIterations = maxIterations;
        this.compressor = compressor;
        this.dailyRecordManager = dailyRecordManager;
    }

    private void publishObservation(String sessionId, ObservationType type, String content) {
        publishObservation(sessionId, type, content, null);
    }

    private void publishObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        ObservationEvent event = ObservationEvent.of(sessionId, type, content, data);
        vertx.eventBus().publish("ganglia.observations." + sessionId, io.vertx.core.json.JsonObject.mapFrom(event));
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext) {
        logger.debug("Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
        publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, userInput);
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = sessionManager.startTurn(initialContext, userMessage);

        return sessionManager.persist(context)
            .compose(v -> runLoop(context, 0));
    }

    @Override
    public Future<String> resume(String toolOutput, SessionContext initialContext) {
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
                return continueActingOrReason(context, 0);
            });
    }

    private Future<String> continueActingOrReason(SessionContext context, int iteration) {
        Turn current = context.currentTurn();
        if (current != null && current.intermediateSteps() != null) {
            // Find the last assistant message that had tool calls
            Message lastAssistant = null;
            List<Message> steps = current.intermediateSteps();
            for (int i = steps.size() - 1; i >= 0; i--) {
                if (steps.get(i).role() == Role.ASSISTANT && steps.get(i).toolCalls() != null) {
                    lastAssistant = steps.get(i);
                    break;
                }
            }

            if (lastAssistant != null) {
                // Find which of those calls are still unanswered
                Set<String> answeredIds = new HashSet<>();
                for (Message m : steps) {
                    if (m.role() == Role.TOOL) {
                        answeredIds.add(m.toolCallId());
                    }
                }

                List<ToolCall> pending = new ArrayList<>();
                for (ToolCall tc : lastAssistant.toolCalls()) {
                    if (!answeredIds.contains(tc.id())) {
                        pending.add(tc);
                    }
                }

                if (!pending.isEmpty()) {
                    logger.debug("Continuing execution of {} pending tool calls.", pending.size());
                    return act(pending, context)
                        .compose(contextAfterTools ->
                            sessionManager.persist(contextAfterTools)
                                .compose(v -> runLoop(contextAfterTools, iteration + 1))
                        ).recover(err -> {
                            if (err instanceof AgentInterruptException) {
                                return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                            }
                            return Future.failedFuture(err);
                        });
                }
            }
        }
        return runLoop(context, iteration);
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

        List<Message> steps = current.intermediateSteps();

        // 1. Collect all tool call IDs that have already been answered.
        Set<String> answeredIds = new HashSet<>();
        if (steps != null) {
            for (Message m : steps) {
                if (m.role() == Role.TOOL) {
                    answeredIds.add(m.toolCallId());
                }
            }

            // 2. Scan the assistant's messages to find the first tool call not in the 'answered' set.
            for (Message m : steps) {
                if (m.role() == Role.ASSISTANT && m.toolCalls() != null) {
                    for (ToolCall tc : m.toolCalls()) {
                        if (!answeredIds.contains(tc.id())) {
                            return tc;
                        }
                    }
                }
            }
        }
        return null;
    }

    private Future<String> runLoop(SessionContext currentContext, int iteration) {
        if (iteration >= maxIterations) {
            logger.warn("Iteration limit reached ({}) for session: {}", maxIterations, currentContext.sessionId());
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        logger.debug("Loop iteration: {} for session: {}", iteration, currentContext.sessionId());
        return reason(currentContext, iteration)
            .compose(response -> handleDecision(response, currentContext, iteration))
            .recover(err -> {
                if (err instanceof AgentInterruptException) {
                    logger.info("Agent loop interrupted for session: {}. Waiting for user interaction.", currentContext.sessionId());
                    return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                }

                logger.error("Error in agent loop for session: {}", currentContext.sessionId(), err);

                Map<String, Object> errorData = new HashMap<>();
                if (err instanceof LLMException) {
                    LLMException llmErr = (LLMException) err;
                    llmErr.errorCode().ifPresent(code -> errorData.put("errorCode", code));
                    llmErr.httpStatusCode().ifPresent(status -> errorData.put("httpStatusCode", status));
                    llmErr.requestId().ifPresent(id -> errorData.put("requestId", id));
                }

                publishObservation(currentContext.sessionId(), ObservationType.ERROR, err.getMessage(), errorData);
                return Future.failedFuture(err);
            });
    }

    // --- Core Logic Steps ---

    private Future<ModelResponse> reason(SessionContext context, int iteration) {
        logger.debug("Building system prompt for iteration: {}", iteration);
        publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);
        // 2. Reason: Construct Prompt and Call Model
        return promptEngine.buildSystemPrompt(context)
            .compose(systemPromptContent -> {
                List<Message> modelHistory = new ArrayList<>();
                // Inject System Prompt for this turn
                modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, null, java.time.Instant.now()));

                // Prune history to fit in model window
                List<Message> fullHistory = context.history();
                List<Message> prunedHistory = pruneHistory(fullHistory, 2000); // Keep last 2000 tokens of history

                modelHistory.addAll(prunedHistory);

                ModelOptions currentOptions = context.modelOptions();
                if (currentOptions == null) {
                    // Fallback should ideally not be reached if SessionManager does its job
                    currentOptions = new ModelOptions(0.0, 4096, "gpt-4o");
                }

                logger.debug("Calling model: {} with history size: {} (pruned from {})",
                    currentOptions.modelName(), modelHistory.size(), fullHistory.size() + 1);
                return model.chatStream(modelHistory, toolExecutor.getAvailableTools(context), currentOptions, context.sessionId());
            });
    }

    private List<Message> pruneHistory(List<Message> history, int maxTokens) {
        if (history == null || history.isEmpty()) return Collections.emptyList();

        List<Message> pruned = new ArrayList<>();
        int currentTokens = 0;

        // Iterate backwards from the most recent messages
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            // Count tokens in content + tool calls
            int msgTokens = tokenCounter.count(msg.content());
            if (msg.toolCalls() != null) {
                msgTokens += tokenCounter.count(msg.toolCalls().toString());
            }

            if (currentTokens + msgTokens > maxTokens && !pruned.isEmpty()) {
                break;
            }
            pruned.add(0, msg);
            currentTokens += msgTokens;
        }
        return pruned;
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, int iteration) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

        logger.debug("Model response content: {}", content);
        logger.debug("Model requested {} tool call(s).", toolCalls.size());
        publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);

        if (hasToolCalls(toolCalls)) {
            // Decision: Act (Execute ALL Tools)
            // Persist the Assistant's intent (Tool Calls) BEFORE executing, so we can resume if interrupted.
            Message assistantMessage = Message.assistant(content, toolCalls);
            SessionContext nextContext = sessionManager.addStep(currentContext, assistantMessage);

            return sessionManager.persist(nextContext)
                .compose(v -> act(toolCalls, nextContext))
                .compose(contextAfterTools ->
                    // Loop: Recurse
                    sessionManager.persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools, iteration + 1))
                );
        } else {
            logger.debug("No tool calls. Turn complete.");
            publishObservation(currentContext.sessionId(), ObservationType.TURN_FINISHED, content);
            // Decision: Finish
            // Important: We call completeTurn on currentContext because the finishing message
            // reached a final answer.
            // was never added as a 'step'.
            SessionContext finalContext = sessionManager.completeTurn(currentContext, Message.assistant(content));
            
            // Async background reflection for Daily Journal
            if (dailyRecordManager != null && compressor != null && finalContext.currentTurn() != null) {
                String goal = finalContext.currentTurn().userMessage().content();
                compressor.reflect(finalContext.currentTurn())
                    .compose(summary -> dailyRecordManager.record(finalContext.sessionId(), goal, summary))
                    .onFailure(err -> logger.error("Daily Journal reflection failed", err));
            }

            return sessionManager.persist(finalContext)
                .map(v -> content);
        }
    }

    private Future<SessionContext> act(List<ToolCall> toolCalls, SessionContext context) {
        // 3. Act: Execute ALL tool calls sequentially to accumulate context
        return executeToolsSequentially(toolCalls, 0, context);
    }

    private Future<SessionContext> executeToolsSequentially(List<ToolCall> toolCalls, int index, SessionContext currentContext) {
        if (index >= toolCalls.size()) {
            return Future.succeededFuture(currentContext);
        }

        ToolCall call = toolCalls.get(index);
        logger.debug("Executing tool call [{} of {}]: {} ({})", index + 1, toolCalls.size(), call.toolName(), call.id());
        publishObservation(currentContext.sessionId(), ObservationType.TOOL_STARTED, call.toolName(), call.arguments());

        return toolExecutor.execute(call, currentContext)
            .compose(invokeResult -> {
                if (invokeResult.status() == ToolInvokeResult.Status.INTERRUPT) {
                    logger.debug("Tool {} requested interrupt.", call.toolName());
                    publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, "Interrupted: " + invokeResult.output());
                    return Future.failedFuture(new AgentInterruptException(invokeResult.output()));
                }

                if (invokeResult.status() == ToolInvokeResult.Status.ERROR || invokeResult.status() == ToolInvokeResult.Status.EXCEPTION) {
                    logger.warn("Tool {} failed with status {}: {}", call.toolName(), invokeResult.status(), invokeResult.output());
                    publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, "Error: " + invokeResult.output());
                } else {
                    logger.debug("Tool {} executed successfully.", call.toolName());
                    publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, invokeResult.output());
                }

                Message toolMsg = Message.tool(call.id(), call.toolName(), invokeResult.output());
                SessionContext contextToUse = invokeResult.modifiedContext() != null ? invokeResult.modifiedContext() : currentContext;
                return Future.succeededFuture(sessionManager.addStep(contextToUse, toolMsg));
            })
            .compose(nextContext -> sessionManager.persist(nextContext).map(v -> nextContext))
            .compose(nextContext -> executeToolsSequentially(toolCalls, index + 1, nextContext));
    }

    private boolean hasToolCalls(List<ToolCall> toolCalls) {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
