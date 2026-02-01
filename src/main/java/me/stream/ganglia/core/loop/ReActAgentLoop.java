package me.stream.ganglia.core.loop;

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

        ModelOptions currentOptions = currentContext.modelOptions();
        if (currentOptions == null) {
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
                        // 3a. Execution Phase - SEQUENTIAL / SINGLE STEP
                        // We take only the FIRST tool call to ensure we reason again after it.
                        // This handles dependencies: Tool B might depend on Tool A's result.
                        ToolCall firstToolCall = toolCalls.get(0);
                        
                        return toolExecutor.execute(firstToolCall)
                                .map(result -> Message.tool(firstToolCall.id(), result))
                                .compose(toolMsg -> {
                                    // Update context
                                    SessionContext contextWithTool = nextContext.withNewMessage(toolMsg);
                                    
                                    // Save state and Continue Loop
                                    return stateEngine.saveSession(contextWithTool)
                                            .compose(voidRes -> runLoop(contextWithTool, iteration + 1));
                                });
                    } else {
                        // 3b. Final Answer
                        return stateEngine.saveSession(nextContext)
                                .map(v -> content);
                    }
                });
    }
}