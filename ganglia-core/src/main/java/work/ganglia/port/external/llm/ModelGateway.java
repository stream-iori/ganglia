package work.ganglia.port.external.llm;

import io.vertx.core.Future;
import work.ganglia.port.internal.state.ExecutionContext;

/** Interface for LLM providers. */
public interface ModelGateway {

  /** Sends a chat completion request to the LLM. */
  Future<ModelResponse> chat(ChatRequest request);

  /**
   * Streaming version of chat. Tokens are emitted via the provided execution context. Returns the
   * complete accumulated response when finished.
   */
  Future<ModelResponse> chatStream(ChatRequest request, ExecutionContext context);
}
