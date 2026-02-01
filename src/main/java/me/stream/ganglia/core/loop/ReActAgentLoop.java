package me.stream.ganglia.core.loop;

import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public class ReActAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final ModelGateway model;
    private final ToolExecutor toolExecutor;
    private final StateEngine stateEngine;
    private final PromptEngine promptEngine;
    private final int maxIterations;
    private final ModelOptions modelOptions;

    public ReActAgentLoop(ModelGateway model, ToolExecutor toolExecutor, StateEngine stateEngine, PromptEngine promptEngine, int maxIterations) {
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.stateEngine = stateEngine;
        this.promptEngine = promptEngine;
        this.maxIterations = maxIterations;
        this.modelOptions = new ModelOptions(0.0, 4096, "default-model");
    }

    @Override
    public CompletionStage<String> run(String userInput, SessionContext initialContext) {
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = initialContext.withNewMessage(userMessage);

        return stateEngine.saveSession(context)
                .thenCompose(v -> runLoop(context, 0));
    }

    private CompletionStage<String> runLoop(SessionContext currentContext, int iteration) {
        if (iteration >= maxIterations) {
            return CompletableFuture.completedFuture("Max iterations reached without final answer.");
        }

        // 2. Reasoning Phase
        String systemPromptContent = promptEngine.buildSystemPrompt(currentContext);
        // Note: In a real system, system prompt usually goes to the context history or is handled by ModelGateway.
        // Here we assume ModelGateway handles it via 'history' if we prepend it, or we rely on ModelGateway API to take system prompt separately.
        // For simplicity, let's assume we pass full history and ModelGateway handles it.
        // But wait, the interface only takes history. So we should prepend system message if it's not there?
        // Let's assume the ModelGateway implementations are smart or we assume history contains it.
        // Ideally, we should add a System message to history temporarily for the call.
        
        // Let's construct a history list that includes the system prompt for the call
        // (but maybe not save it to permanent session history if we regenerate it every time?)
        // The design says "AgentLoop -> PromptEngine: buildSystemPrompt".
        // Let's create a temporary list for the model call.
        List<Message> modelHistory = new java.util.ArrayList<>();
        modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, java.time.Instant.now()));
        modelHistory.addAll(currentContext.history());

        return model.chat(modelHistory, toolExecutor.getAvailableTools(), modelOptions)
                .thenCompose(response -> {
                    // 3. Process Response
                    String content = response.content();
                    List<ToolCall> toolCalls = response.toolCalls();
                    
                    Message assistantMessage = Message.assistant(content, toolCalls);
                    SessionContext nextContext = currentContext.withNewMessage(assistantMessage);

                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        // 3a. Execution Phase
                        // Execute all tools in parallel or sequence. Parallel is faster.
                        List<CompletableFuture<Message>> toolExecutionFutures = toolCalls.stream()
                                .map(call -> toolExecutor.execute(call)
                                        .thenApply(result -> Message.tool(call.id(), result))
                                        .toCompletableFuture())
                                .collect(Collectors.toList());

                        return CompletableFuture.allOf(toolExecutionFutures.toArray(new CompletableFuture[0]))
                                .thenCompose(v -> {
                                    // Collect results
                                    List<Message> toolMessages = toolExecutionFutures.stream()
                                            .map(CompletableFuture::join)
                                            .collect(Collectors.toList());
                                    
                                    // Update context with ALL tool results
                                    SessionContext contextWithTools = nextContext;
                                    for (Message msg : toolMessages) {
                                        contextWithTools = contextWithTools.withNewMessage(msg);
                                    }

                                    // Save state and Continue Loop
                                    SessionContext finalContextWithTools = contextWithTools;
                                    return stateEngine.saveSession(finalContextWithTools)
                                            .thenCompose(voidRes -> runLoop(finalContextWithTools, iteration + 1));
                                });
                    } else {
                        // 3b. Final Answer
                        return stateEngine.saveSession(nextContext)
                                .thenApply(v -> content);
                    }
                });
    }
}
