package work.ganglia.port.external.llm;

import io.vertx.core.Future;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.internal.state.AgentSignal;

import java.util.List;

public interface ModelGateway {

    /**
     * Sends a chat completion request to the LLM.
     * @param history The conversation history.
     * @param availableTools List of tools available to the model.
     * @param options Model parameters (model name, temperature, etc).
     * @param signal The abort signal to monitor for cancellation.
     */
    Future<ModelResponse> chat(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options,
        AgentSignal signal
    );

    /**
     * Streaming version of chat.
     * Publishes observation events to the EventBus.
     * Returns the complete accumulated response when finished.
     * @param history The conversation history.
     * @param availableTools List of tools available to the model.
     * @param options Model parameters.
     * @param sessionId The session ID for publishing tokens.
     * @param signal The abort signal to monitor for cancellation.
     */
    Future<ModelResponse> chatStream(
        List<Message> history,
        List<ToolDefinition> availableTools,
        ModelOptions options,
        String sessionId,
        AgentSignal signal
    );
}
