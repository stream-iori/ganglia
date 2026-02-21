package me.stream.ganglia.core.prompt;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.LLMRequest;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.SessionContext;

import java.util.List;

public interface PromptEngine {

    /**
     * Generates the System Message based on current context.
     */
    Future<String> buildSystemPrompt(SessionContext context);

    /**
     * Prepares a full LlmRequest by constructing the system prompt,
     * pruning history, and resolving model options and tools.
     *
     * @param context The current session context.
     * @param iteration The current iteration in the ReAct loop.
     * @return A Future containing the fully prepared LlmRequest.
     */
    Future<LLMRequest> prepareRequest(SessionContext context, int iteration);
}
