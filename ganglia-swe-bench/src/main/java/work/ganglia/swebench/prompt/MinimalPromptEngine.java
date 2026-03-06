package work.ganglia.swebench.prompt;

import io.vertx.core.Future;
import work.ganglia.core.model.LLMRequest;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.prompt.PromptEngine;
import work.ganglia.tools.ToolExecutor;

import java.util.ArrayList;
import java.util.List;

public class MinimalPromptEngine implements PromptEngine {
    private final ToolExecutor toolExecutor;

    public MinimalPromptEngine(ToolExecutor toolExecutor) {
        this.toolExecutor = toolExecutor;
    }

    @Override
    public Future<String> buildSystemPrompt(SessionContext context) {
        String problemStatement = (String) context.metadata().get("problem_statement");
        return Future.succeededFuture("""
            You are a senior software engineer tasked with fixing a bug or implementing a feature.

            PROBLEM STATEMENT:
            %s

            You have access to tools to explore the codebase and apply fixes inside a Docker sandbox.
            Your goal is to resolve the issue described above.

            Rules:
            1. Explore the codebase first to understand the issue.
            2. Implement the fix using available tools.
            3. Verify your changes if possible.
            4. Respond with your final answer when you are done.
            """.formatted(problemStatement));
    }

    @Override
    public Future<LLMRequest> prepareRequest(SessionContext context, int iteration) {
        return buildSystemPrompt(context).map(systemPrompt -> {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(systemPrompt));
            messages.addAll(context.history());

            ModelOptions options = context.modelOptions();
            if (options == null) {
                options = new ModelOptions(0.0, 4096, "gpt-4o", true);
            }
            return new LLMRequest(messages, toolExecutor.getAvailableTools(context), options);
        });
    }
}
