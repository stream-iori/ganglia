package work.ganglia.port.external.llm;

import io.vertx.core.Future;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolDefinition;

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
