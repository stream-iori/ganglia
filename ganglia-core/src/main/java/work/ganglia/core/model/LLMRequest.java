package work.ganglia.core.model;

import work.ganglia.tools.model.ToolDefinition;
import java.util.List;

/**
 * Encapsulates all data required for an LLM request.
 */
public record LLMRequest(
    List<Message> messages,
    List<ToolDefinition> tools,
    ModelOptions options
) {}
