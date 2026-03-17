package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.port.external.llm.LLMRequest;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.PromptEngine;
import work.ganglia.port.external.tool.ToolDefinition;

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
