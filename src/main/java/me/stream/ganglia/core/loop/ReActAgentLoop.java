package me.stream.ganglia.core.loop;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.core.tools.ToolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ReActAgentLoop implements AgentLoop {
    private static final Logger logger = LoggerFactory.getLogger(ReActAgentLoop.class);

    private final ModelGateway model;
    private final ToolExecutor toolExecutor;
    private final StateEngine stateEngine;
    private final PromptEngine promptEngine;
    private final int maxIterations;

    public ReActAgentLoop(ModelGateway model, ToolExecutor toolExecutor, StateEngine stateEngine, PromptEngine promptEngine, int maxIterations) {
        this.model = model;
        this.toolExecutor = toolExecutor;
        this.stateEngine = stateEngine;
        this.promptEngine = promptEngine;
        this.maxIterations = maxIterations;
    }

    @Override
    public Future<String> run(String userInput, SessionContext initialContext) {
        // 1. Initialization
        Message userMessage = Message.user(userInput);
        SessionContext context = initialContext.withNewMessage(userMessage);

        return stateEngine.saveSession(context)
                .compose(v -> runLoop(context, 0));
    }

    private Future<String> runLoop(SessionContext currentContext, int iteration) {
        if (iteration >= maxIterations) {
            return Future.succeededFuture("Max iterations reached without final answer.");
        }

        // 2. Reasoning Phase
        String systemPromptContent = promptEngine.buildSystemPrompt(currentContext);
        List<Message> modelHistory = new ArrayList<>();
        modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, java.time.Instant.now()));
        modelHistory.addAll(currentContext.history());

        // Use ModelOptions from the Context
        ModelOptions currentOptions = currentContext.modelOptions();
        if (currentOptions == null) {
             // Fallback default if not set
             currentOptions = new ModelOptions(0.0, 4096, "default-model");
        }

        return model.chat(modelHistory, toolExecutor.getAvailableTools(), currentOptions)
                .compose(response -> {
                    // 3. Process Response
                    String content = response.content();
                    List<ToolCall> toolCalls = response.toolCalls();
                    
                    Message assistantMessage = Message.assistant(content, toolCalls);
                    SessionContext nextContext = currentContext.withNewMessage(assistantMessage);

                    if (toolCalls != null && !toolCalls.isEmpty()) {
                        // 3a. Execution Phase
                        List<Future> toolExecutionFutures = toolCalls.stream()
                                .map(call -> toolExecutor.execute(call)
                                        .map(result -> Message.tool(call.id(), result)))
                                .collect(Collectors.toList());

                        List futures = new ArrayList<>(toolExecutionFutures);
                        return Future.all(futures)
                                .compose(composite -> {
                                    // Collect results
                                    List<Message> toolMessages = toolExecutionFutures.stream()
                                            .map(f -> (Message) f.result())
                                            .collect(Collectors.toList());
                                    
                                    // Update context with ALL tool results
                                    SessionContext contextWithTools = nextContext;
                                    for (Message msg : toolMessages) {
                                        contextWithTools = contextWithTools.withNewMessage(msg);
                                    }

                                    // Save state and Continue Loop
                                    SessionContext finalContextWithTools = contextWithTools;
                                    return stateEngine.saveSession(finalContextWithTools)
                                            .compose(voidRes -> runLoop(finalContextWithTools, iteration + 1));
                                });
                    } else {
                        // 3b. Final Answer
                        return stateEngine.saveSession(nextContext)
                                .map(v -> content);
                    }
                });
    }
}
