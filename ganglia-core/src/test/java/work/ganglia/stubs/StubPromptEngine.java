package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.core.model.LLMRequest;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.prompt.PromptEngine;
import work.ganglia.tools.model.ToolDefinition;

import java.util.Collections;
import java.util.List;

public class StubPromptEngine implements PromptEngine {

    @Override
    public Future<String> buildSystemPrompt(SessionContext context) {
        return Future.succeededFuture("System Prompt");
    }

    @Override
    public Future<LLMRequest> prepareRequest(SessionContext context, int iteration) {
        List<ToolDefinition> tools = Collections.emptyList();
        List<Message> messages = List.of(
            Message.system("System Prompt"),
            Message.user("User Prompt")
        );
        return Future.succeededFuture(new LLMRequest(
                messages,
                tools,
                context.modelOptions()
        ));
    }
}
