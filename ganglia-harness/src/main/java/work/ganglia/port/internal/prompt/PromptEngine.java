package work.ganglia.port.internal.prompt;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.LLMRequest;

public interface PromptEngine {

  /** Generates the System Message based on current context. */
  Future<String> buildSystemPrompt(SessionContext context);

  /**
   * Prepares a full LlmRequest by constructing the system prompt, pruning history, and resolving
   * model options and tools.
   *
   * @param context The current session context.
   * @param iteration The current iteration in the Standard loop.
   * @return A Future containing the fully prepared LlmRequest.
   */
  Future<LLMRequest> prepareRequest(SessionContext context, int iteration);
}
