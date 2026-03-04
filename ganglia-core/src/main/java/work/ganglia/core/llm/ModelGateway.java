package work.ganglia.core.llm;

import io.vertx.core.Future;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.tools.model.ToolDefinition;

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
