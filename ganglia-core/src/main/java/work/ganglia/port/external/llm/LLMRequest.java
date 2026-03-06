package work.ganglia.port.external.llm;

import work.ganglia.port.external.tool.ToolDefinition;
import java.util.List;
import work.ganglia.port.chat.Message;

/**
 * Encapsulates all data required for an LLM request.
 */
public record LLMRequest(
    List<Message> messages,
    List<ToolDefinition> tools,
    ModelOptions options
) {}
