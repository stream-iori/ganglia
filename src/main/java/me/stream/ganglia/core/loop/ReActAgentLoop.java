package me.stream.ganglia.core.loop;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.tools.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.LogManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import me.stream.ganglia.core.tools.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ReActAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final ModelGateway model;
    private final ToolExecutor toolExecutor;
    private final StateEngine stateEngine;
    private final LogManager logManager;
    private final PromptEngine promptEngine;
    private final int maxIterations;

    public ReActAgentLoop(ModelGateway model, ToolExecutor toolExecutor, StateEngine stateEngine, LogManager logManager, PromptEngine promptEngine, int maxIterations) {
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.stateEngine = stateEngine;
        this.logManager = logManager;
        this.promptEngine = promptEngine;
        this.maxIterations = maxIterations;
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext) {
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = initialContext.withNewMessage(userMessage);

        return persist(context)
                .compose(v -> runLoop(context, 0));
    }

    @Override
    public Future<String> resume(String toolOutput, SessionContext initialContext) {
        // Find the last pending tool call ID from context
        String toolCallId = findPendingToolCallId(initialContext);
        if (toolCallId == null) {
            return Future.failedFuture("No pending tool call found to resume.");
        }

        Message toolMessage = Message.tool(toolCallId, toolOutput);
        SessionContext context = initialContext.withNewMessage(toolMessage);

        return persist(context)
                .compose(v -> runLoop(context, 0));
    }

    private Future<Void> persist(SessionContext context) {
        return stateEngine.saveSession(context)
                .compose(v -> logManager != null ? logManager.appendLog(context) : Future.succeededFuture());
    }

    private String findPendingToolCallId(SessionContext context) {
        Turn current = context.currentTurn();
        if (current == null) return null;
        
        List<Message> steps = current.intermediateSteps();
        // Check steps backwards or check assistant message?
        // In my logic, Assistant Message is added, then Tool Messages are added.
        // If interrupted, the last message in turn might be the Assistant Message (if no tools finished yet)
        // OR a Tool Message (if some finished, but one interrupted).
        
        // Actually, 'intermediateSteps' contains both Assistant (Thought/Calls) and Tool (Results).
        // I need to find the last Assistant message with tool calls, and find which one is missing a result?
        // Simplified: Assume the LAST message with toolCalls is the one. And take the LAST toolCall in it?
        // Or the first one that doesn't have a matching ToolMessage?
        
        // Since I execute sequentially, if I interrupted, it was on the *current* tool call.
        // But I don't know *which* index I was on easily without state.
        // But if I return "Interrupt", I haven't added the ToolMessage yet.
        // So I just need to find the tool call that has no corresponding tool message.
        
        // Iterate steps to find all Tool Calls and Tool Messages.
        // Match them. The first unmatched one is the one to resume.
        // Wait, steps are mixed.
        
        // This logic is getting complex for a simple 'findPending'.
        // I'll scan the current turn for the last Assistant message with tool calls.
        // Then assume the last tool call in that message is the one (or the one pending).
        // Since I execute sequentially, and stop on interrupt, it should be the *first* one that isn't answered?
        // Or if I executed 2 successful ones, then interrupted on 3rd.
        
        // Let's implement a simple matcher.
        java.util.Set<String> answeredIds = new java.util.HashSet<>();
        if (steps != null) {
            for (Message m : steps) {
                if (m.role() == Role.TOOL) {
                    answeredIds.add(m.toolCallId());
                }
            }
            
            // Go through steps to find unmatched tool call
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
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        return reason(currentContext, iteration)
                .compose(response -> handleDecision(response, currentContext, iteration))
                .recover(err -> {
                    if (err instanceof AgentInterruptException) {
                        return Future.succeededFuture(((AgentInterruptException) err).getPrompt());
                    }
                    return Future.failedFuture(err);
                });
    }

    // --- Core Logic Steps ---

    private Future<ModelResponse> reason(SessionContext context, int iteration) {
        // 2. Reason: Construct Prompt and Call Model
        return promptEngine.buildSystemPrompt(context)
            .compose(systemPromptContent -> {
                List<Message> modelHistory = new ArrayList<>();
                // Inject System Prompt for this turn
                modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, java.time.Instant.now()));
                modelHistory.addAll(context.history());

                ModelOptions currentOptions = context.modelOptions();
                if (currentOptions == null) {
                     currentOptions = new ModelOptions(0.0, 4096, "default-model");
                }

                String streamAddr = "ganglia.stream." + context.sessionId();
                return model.chatStream(modelHistory, toolExecutor.getAvailableTools(context), currentOptions, streamAddr);
            });
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, int iteration) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();

                Message assistantMessage = Message.assistant(content, toolCalls);

                SessionContext nextContext = currentContext.withNewMessage(assistantMessage);

        

                if (hasToolCalls(toolCalls)) {

                    // Decision: Act (Execute ALL Tools)

                    // Persist the Assistant's intent (Tool Calls) BEFORE executing, so we can resume if interrupted.

                    return persist(nextContext)

                            .compose(v -> act(toolCalls, nextContext))

                            .compose(contextAfterTools -> 

                                // Loop: Recurse

        
                                    persist(contextAfterTools)
                                            .compose(v -> runLoop(contextAfterTools, iteration + 1))
                                );
                    } else {
                        // Decision: Finish
                        return persist(nextContext)
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
                            return toolExecutor.execute(call, currentContext)
                                    .compose(invokeResult -> {
                                        if (invokeResult.status() == me.stream.ganglia.core.tools.model.ToolInvokeResult.Status.INTERRUPT) {
                                            return Future.failedFuture(new AgentInterruptException(invokeResult.output()));
                                        }
                                        Message toolMsg = Message.tool(call.id(), invokeResult.output());
                                        SessionContext contextToUse = invokeResult.modifiedContext() != null ? invokeResult.modifiedContext() : currentContext;
                                        return Future.succeededFuture(contextToUse.withNewMessage(toolMsg));
                                    })
                                    .compose(nextContext -> persist(nextContext).map(v -> nextContext))
                                    .compose(nextContext -> executeToolsSequentially(toolCalls, index + 1, nextContext));
                        }
                    
                private boolean hasToolCalls(List<ToolCall> toolCalls) {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
