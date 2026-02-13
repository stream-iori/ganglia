package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.tools.model.*;
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

    public ReActAgentLoop(ModelGateway model, ToolExecutor toolExecutor, SessionManager sessionManager, PromptEngine promptEngine, int maxIterations) {
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.sessionManager = sessionManager;
        this.promptEngine = promptEngine;
        this.maxIterations = maxIterations;
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext) {
        logger.debug("Starting new turn for session: {}. Input: {}", initialContext.sessionId(), userInput);
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = sessionManager.startTurn(initialContext, userMessage);

        return sessionManager.persist(context)
            .compose(v -> runLoop(context, 0));
    }

    @Override
    public Future<String> resume(String toolOutput, SessionContext initialContext) {
        logger.debug("Resuming session: {}. Tool output received.", initialContext.sessionId());
        // Find the last pending tool call ID from context
        String toolCallId = findPendingToolCallId(initialContext);
        if (toolCallId == null) {
            logger.error("Resume failed: No pending tool call found for session: {}", initialContext.sessionId());
            return Future.failedFuture("No pending tool call found to resume.");
        }

        logger.debug("Matched resume output to toolCallId: {}", toolCallId);
        Message toolMessage = Message.tool(toolCallId, toolOutput);
        SessionContext context = sessionManager.addStep(initialContext, toolMessage);

        return sessionManager
            .persist(context)
            .compose(v -> runLoop(context, 0));
    }

    /**
     * Identifies a tool call that has been requested by the Assistant but has not yet
     * received a corresponding result from the Tool system. This is crucial for
     * resuming a session after an interrupt (e.g., user confirmation).
     *
     * @param context The current session context containing the turn history.
     * @return The ID of the pending tool call, or null if all calls are accounted for.
     */
    private String findPendingToolCallId(SessionContext context) {
        Turn current = context.currentTurn();
        if (current == null) return null;

        List<Message> steps = current.intermediateSteps();

        // 1. Collect all tool call IDs that have already been answered.
        // In the ReAct loop, a successful tool execution appends a message with Role.TOOL
        // and a specific toolCallId.
        Set<String> answeredIds = new HashSet<>();
        if (steps != null) {
            for (Message m : steps) {
                if (m.role() == Role.TOOL) {
                    answeredIds.add(m.toolCallId());
                }
            }

            // 2. Scan the assistant's messages to find the first tool call not in the 'answered' set.
            // Assistant messages contain the "intent" (ToolCalls), while Tool messages contain the "result".
            // If we find a tool call ID that hasn't been added to answeredIds, it means this specific
            // action was requested but its execution was interrupted or failed before completion.
            for (Message m : steps) {
                if (m.role() == Role.ASSISTANT && m.toolCalls() != null) {
                    for (ToolCall tc : m.toolCalls()) {
                        if (!answeredIds.contains(tc.id())) {
                            return tc.id();
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
                return Future.failedFuture(err);
            });
    }

    // --- Core Logic Steps ---

    private Future<ModelResponse> reason(SessionContext context, int iteration) {
        logger.debug("Building system prompt for iteration: {}", iteration);
        // 2. Reason: Construct Prompt and Call Model
        return promptEngine.buildSystemPrompt(context)
            .compose(systemPromptContent -> {
                List<Message> modelHistory = new ArrayList<>();
                // Inject System Prompt for this turn
                modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, java.time.Instant.now()));
                modelHistory.addAll(context.history());

                ModelOptions currentOptions = context.modelOptions();
                if (currentOptions == null) {
                    // Fallback should ideally not be reached if SessionManager does its job
                    currentOptions = new ModelOptions(0.0, 4096, "gpt-4o");
                }

                logger.debug("Calling model: {} with history size: {}", currentOptions.modelName(), modelHistory.size());
                String streamAddr = "ganglia.stream." + context.sessionId();
                return model.chatStream(modelHistory, toolExecutor.getAvailableTools(context), currentOptions, streamAddr);
            });
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, int iteration) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

        if (content != null && !content.isEmpty()) {
            logger.debug("Model response content: {}", content);
        }
        
        if (hasToolCalls(toolCalls)) {
            logger.debug("Model requested {} tool call(s).", toolCalls.size());
        }

        Message assistantMessage = Message.assistant(content, toolCalls);
        SessionContext nextContext = sessionManager.addStep(currentContext, assistantMessage);

        if (hasToolCalls(toolCalls)) {
            // Decision: Act (Execute ALL Tools)
            // Persist the Assistant's intent (Tool Calls) BEFORE executing, so we can resume if interrupted.
            return sessionManager.persist(nextContext)
                .compose(v -> act(toolCalls, nextContext))
                .compose(contextAfterTools ->
                    // Loop: Recurse
                    sessionManager.persist(contextAfterTools)
                        .compose(v -> runLoop(contextAfterTools, iteration + 1))
                );
        } else {
            logger.debug("No tool calls. Turn complete.");
            // Decision: Finish
            SessionContext finalContext = sessionManager.completeTurn(nextContext, Message.assistant(content));
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
        
        return toolExecutor.execute(call, currentContext)
            .compose(invokeResult -> {
                if (invokeResult.status() == ToolInvokeResult.Status.INTERRUPT) {
                    logger.debug("Tool {} requested interrupt.", call.toolName());
                    return Future.failedFuture(new AgentInterruptException(invokeResult.output()));
                }
                
                if (invokeResult.status() == ToolInvokeResult.Status.ERROR || invokeResult.status() == ToolInvokeResult.Status.EXCEPTION) {
                    logger.warn("Tool {} failed with status {}: {}", call.toolName(), invokeResult.status(), invokeResult.output());
                } else {
                    logger.debug("Tool {} executed successfully.", call.toolName());
                }

                Message toolMsg = Message.tool(call.id(), invokeResult.output());
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
