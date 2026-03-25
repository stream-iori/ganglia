package work.ganglia.port.external.llm;

import java.util.List;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.tool.ToolDefinition;

/** Encapsulates all data required for an LLM request. */
public record LLMRequest(
    List<Message> messages, List<ToolDefinition> tools, ModelOptions options) {}
