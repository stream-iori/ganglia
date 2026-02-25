package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.LLMException;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.ReflectEvent;
import me.stream.ganglia.memory.TokenCounter;
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
    private final ConfigManager configManager;
    private final Vertx vertx;
    private final ContextCompressor compressor;
    private final TokenCounter tokenCounter = new TokenCounter();

    public ReActAgentLoop(Vertx vertx, ModelGateway model, ToolExecutor toolExecutor, SessionManager sessionManager,
                          PromptEngine promptEngine, ConfigManager configManager, ContextCompressor compressor) {
        this.vertx = vertx;
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
        this.compressor = compressor;
    }

    public PromptEngine promptEngine() {
        return promptEngine;
    }

    private void publishObservation(String sessionId, ObservationType type, String content) {
        publishObservation(sessionId, type, content, null);
    }

    private void publishObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        ObservationEvent event = ObservationEvent.of(sessionId, type, content, data);
        vertx.eventBus().publish("ganglia.observations." + sessionId, JsonObject.mapFrom(event));
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext) {
        logger.debug("Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
        publishObservation(initialContext.sessionId(), ObservationType.TURN_STARTED, userInput);
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = sessionManager.startTurn(initialContext, userMessage);
        return sessionManager.persist(context).compose(v -> runLoop(context));
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
                return continueActingOrReason(context);
            });
    }

    private Future<String> continueActingOrReason(SessionContext context) {
        Turn current = context.currentTurn();
        if (current != null) {
            List<ToolCall> pending = current.getPendingToolCalls();

            if (!pending.isEmpty()) {
                logger.debug("Continuing execution of {} pending tool calls.", pending.size());
                return act(pending, context)
                    .compose(contextAfterTools ->
                        sessionManager
                            .persist(contextAfterTools)
                            .compose(v -> runLoop(contextAfterTools))
                    )
                    .recover(err -> {
                        if (err instanceof AgentInterruptException) {
                            return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                        }
                        return Future.failedFuture(err);
                    });
            }
        }
        return runLoop(context);
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

    private Future<String> runLoop(SessionContext currentContext) {
        int iteration = currentContext.getIterationCount();
        int maxIterations = configManager.getMaxIterations();
        if (iteration >= maxIterations) {
            logger.warn("Iteration limit reached ({}) for session: {}", maxIterations, currentContext.sessionId());
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        // --- Proactive Context Compression Check ---
        int totalTokens = currentContext.history().stream()
                .mapToInt(m -> m.countTokens(tokenCounter))
                .sum();
        
        int limit = configManager.getContextLimit();
        double threshold = configManager.getCompressionThreshold();

        // --- Robustness: Financial Guardrail ---
        if (totalTokens > 500000) { // Hard limit of 500k tokens per session
            logger.error("Session token limit exceeded ({}). Aborting loop.", totalTokens);
            publishObservation(currentContext.sessionId(), ObservationType.ERROR, "Session token limit exceeded. Safety abort.");
            return Future.failedFuture("Session reached maximum safety token limit (500,000).");
        }

        if (totalTokens > limit * threshold && currentContext.previousTurns().size() > 1) {
            logger.info("Context threshold reached ({} > {}). Triggering compression...", totalTokens, (int)(limit * threshold));
            publishObservation(currentContext.sessionId(), ObservationType.SYSTEM_EVENT, "Auto-compressing context...");
            
            return sessionManager.compressSession(currentContext, 1, compressor)
                    .compose(compressedContext -> {
                        logger.info("Compression complete. New token count: {}", 
                            compressedContext.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum());
                        return runLoop(compressedContext); // Restart this iteration with compressed context
                    });
        }

        logger.debug("Loop iteration: {} for session: {}", iteration, currentContext.sessionId());
        return reason(currentContext)
            .compose(response -> handleDecision(response, currentContext))
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

    private Future<ModelResponse> reason(SessionContext context) {
        return retryReason(context, 0);
    }

    private Future<ModelResponse> retryReason(SessionContext context, int attempt) {
        int iteration = context.getIterationCount();
        if (attempt == 0) {
            publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);
        }

        return promptEngine.prepareRequest(context, iteration)
            .compose(request -> {
                logger.debug("Calling model: {} with history size: {} (Attempt: {})",
                    request.options().modelName(), request.messages().size(), attempt + 1);
                return model.chatStream(request.messages(), request.tools(), request.options(), context.sessionId());
            })
            .recover(err -> {
                if (shouldRetry(err) && attempt < 3) { // Max 3 retries
                    long delay = calculateDelay(attempt);
                    logger.warn("Transient LLM error for session {}. Retrying in {}ms... Error: {}", 
                        context.sessionId(), delay, err.getMessage());
                    
                    Promise<ModelResponse> promise = Promise.promise();
                    vertx.setTimer(delay, id -> {
                        retryReason(context, attempt + 1).onComplete(promise);
                    });
                    return promise.future();
                }
                return Future.failedFuture(err);
            });
    }

    private boolean shouldRetry(Throwable err) {
        if (err instanceof LLMException) {
            LLMException le = (LLMException) err;
            int status = le.httpStatusCode().orElse(0);
            return status == 429 || (status >= 500 && status < 600);
        }
        return false;
    }

    private long calculateDelay(int attempt) {
        long base = 1000; // 1s
        long delay = (long) (base * Math.pow(2, attempt));
        long jitter = (long) (Math.random() * 500); // 0-500ms jitter
        return delay + jitter;
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

        logger.debug("Model response content: {}", content);
        logger.debug("Model requested {} tool call(s).", toolCalls.size());
        publishObservation(currentContext.sessionId(), ObservationType.REASONING_FINISHED, content);

        // Record usage asynchronously
        if (response.usage() != null) {
            vertx.eventBus().publish("ganglia.usage.record", new io.vertx.core.json.JsonObject()
                .put("sessionId", currentContext.sessionId())
                .put("usage", io.vertx.core.json.JsonObject.mapFrom(response.usage())));
        }

        if (!toolCalls.isEmpty()) {
            // Decision: Act (Execute ALL Tools)
            // Persist the Assistant's intent (Tool Calls) BEFORE executing, so we can resume if interrupted.
            Message assistantMessage = Message.assistant(content, toolCalls);
            SessionContext nextContext = sessionManager.addStep(currentContext, assistantMessage);

            return sessionManager.persist(nextContext)
                .compose(v -> act(toolCalls, nextContext))
                .compose(contextAfterTools ->
                    // Loop: Recurse
                    sessionManager.persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools))
                );
        } else {
            logger.debug("No tool calls. Turn complete.");
            publishObservation(currentContext.sessionId(), ObservationType.TURN_FINISHED, content);
            // Decision: Finish
            SessionContext finalContext = sessionManager.completeTurn(currentContext, Message.assistant(content));

            // Async background reflection for Daily Journal via EventBus
            if (finalContext.currentTurn() != null) {
                String goal = finalContext.currentTurn().userMessage().content();
                ReflectEvent event = new ReflectEvent(finalContext.sessionId(), goal, finalContext.currentTurn());
                vertx.eventBus().publish("ganglia.memory.reflect", JsonObject.mapFrom(event));
            }

            return sessionManager
                .persist(finalContext)
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
                switch (invokeResult.status()) {
                    case INTERRUPT -> {
                        logger.debug("Tool {} requested interrupt.", call.toolName());
                        publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, "Interrupted: " + invokeResult.output());
                        return Future.failedFuture(new AgentInterruptException(invokeResult.output()));
                    }
                    case ERROR, EXCEPTION -> {
                        logger.warn("Tool {} failed with status {}: {}", call.toolName(), invokeResult.status(), invokeResult.output());
                        publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, "Error: " + invokeResult.output());
                    }
                    case SUCCESS -> {
                        logger.debug("Tool {} executed successfully.", call.toolName());
                        publishObservation(currentContext.sessionId(), ObservationType.TOOL_FINISHED, invokeResult.output());
                    }
                }

                Message toolMsg = Message.tool(call.id(), call.toolName(), invokeResult.output());
                SessionContext contextToUse = invokeResult.modifiedContext() != null ? invokeResult.modifiedContext() : currentContext;
                
                // --- Robustness: Consecutive Failure Tracking ---
                if (invokeResult.status() == ToolInvokeResult.Status.ERROR || invokeResult.status() == ToolInvokeResult.Status.EXCEPTION) {
                    int fails = (int) contextToUse.metadata().getOrDefault("consecutive_tool_failures", 0);
                    if (fails >= 2) {
                        logger.warn("Tool {} failed {} times consecutively. Aborting loop to prevent runaway usage.", call.toolName(), fails + 1);
                        return Future.failedFuture("Aborting due to repetitive tool failures: " + invokeResult.output());
                    }
                    Map<String, Object> newMetadata = new HashMap<>(contextToUse.metadata());
                    newMetadata.put("consecutive_tool_failures", fails + 1);
                    contextToUse = new SessionContext(contextToUse.sessionId(), contextToUse.previousTurns(), contextToUse.currentTurn(), 
                        newMetadata, contextToUse.activeSkillIds(), contextToUse.modelOptions(), contextToUse.toDoList());
                } else {
                    if (contextToUse.metadata().containsKey("consecutive_tool_failures")) {
                        Map<String, Object> newMetadata = new HashMap<>(contextToUse.metadata());
                        newMetadata.remove("consecutive_tool_failures");
                        contextToUse = new SessionContext(contextToUse.sessionId(), contextToUse.previousTurns(), contextToUse.currentTurn(), 
                            newMetadata, contextToUse.activeSkillIds(), contextToUse.modelOptions(), contextToUse.toDoList());
                    }
                }

                return Future.succeededFuture(sessionManager.addStep(contextToUse, toolMsg));
            })
            .compose(nextContext -> sessionManager.persist(nextContext).map(v -> nextContext))
            .compose(nextContext -> executeToolsSequentially(toolCalls, index + 1, nextContext));
    }
}
