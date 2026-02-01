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

        return reason(currentContext, iteration)
                .compose(response -> handleDecision(response, currentContext, iteration));
    }

    // --- Core Logic Steps ---

    private Future<ModelResponse> reason(SessionContext context, int iteration) {
        // 2. Reason: Construct Prompt and Call Model
        String systemPromptContent = promptEngine.buildSystemPrompt(context);
        List<Message> modelHistory = new ArrayList<>();
        // Inject System Prompt for this turn
        modelHistory.add(new Message("sys-" + iteration, Role.SYSTEM, systemPromptContent, null, null, java.time.Instant.now()));
        modelHistory.addAll(context.history());

        ModelOptions currentOptions = context.modelOptions();
        if (currentOptions == null) {
             currentOptions = new ModelOptions(0.0, 4096, "default-model");
        }

        return model.chat(modelHistory, toolExecutor.getAvailableTools(), currentOptions);
    }

    private Future<String> handleDecision(ModelResponse response, SessionContext currentContext, int iteration) {
        String content = response.content();
        List<ToolCall> toolCalls = response.toolCalls();
        
        Message assistantMessage = Message.assistant(content, toolCalls);
        SessionContext nextContext = currentContext.withNewMessage(assistantMessage);

        if (hasToolCalls(toolCalls)) {
            // Decision: Act (Execute ALL Tools)
            return act(toolCalls, nextContext)
                    .compose(contextAfterTools -> 
                        // Loop: Recurse
                        stateEngine.saveSession(contextAfterTools)
                                .compose(v -> runLoop(contextAfterTools, iteration + 1))
                    );
        } else {
            // Decision: Finish
            return stateEngine.saveSession(nextContext)
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
        return toolExecutor.execute(call)
                .map(invokeResult -> Message.tool(call.id(), invokeResult.output()))
                .map(currentContext::withNewMessage)
                .compose(nextContext -> executeToolsSequentially(toolCalls, index + 1, nextContext));
    }

    private boolean hasToolCalls(List<ToolCall> toolCalls) {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}