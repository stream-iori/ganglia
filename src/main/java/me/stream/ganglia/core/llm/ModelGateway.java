package me.stream.ganglia.core.llm;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.ModelResponse;
import me.stream.ganglia.core.model.ToolDefinition;

import java.util.List;
import java.util.concurrent.Flow; // Java Flow API for streaming

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
     * Publishes tokens to the specified EventBus address.
     */
    Future<Void> chatStream(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options,
        String streamAddress
    );
}
