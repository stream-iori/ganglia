package me.stream.ganglia.core.llm;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.ModelResponse;
import me.stream.ganglia.tools.model.ToolDefinition;

import java.util.List;

public interface ModelGateway {

    /**
     * Sends a chat completion request to the LLM.
     */
    Future<ModelResponse> chat(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options
    );

    /**
     * Streaming version of chat.
     * Publishes observation events to the EventBus.
     * Returns the complete accumulated response when finished.
     */
    Future<ModelResponse> chatStream(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options,
        String sessionId
    );
}
