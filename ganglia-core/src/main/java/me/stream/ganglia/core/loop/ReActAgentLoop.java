package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.LLMException;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.memory.ReflectEvent;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToolCall;
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

    public ReActAgentLoop(Vertx vertx, ModelGateway model, ToolExecutor toolExecutor, SessionManager sessionManager,
                          PromptEngine promptEngine, ConfigManager configManager) {
        this.vertx = vertx;
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.configManager = configManager;
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
        int iteration = context.getIterationCount();
        logger.debug("Building LLM request for iteration: {}", iteration);
        publishObservation(context.sessionId(), ObservationType.REASONING_STARTED, null);

        return promptEngine.prepareRequest(context, iteration)
            .compose(request -> {
                logger.debug("Calling model: {} with history size: {}",
                    request.options().modelName(), request.messages().size());
                return model.chatStream(request.messages(), request.tools(), request.options(), context.sessionId());
            });
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
                return Future.succeededFuture(sessionManager.addStep(contextToUse, toolMsg));
            })
            .compose(nextContext -> sessionManager.persist(nextContext).map(v -> nextContext))
            .compose(nextContext -> executeToolsSequentially(toolCalls, index + 1, nextContext));
    }
}
