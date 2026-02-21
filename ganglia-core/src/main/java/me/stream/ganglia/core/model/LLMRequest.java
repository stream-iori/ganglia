package me.stream.ganglia.core.model;

import me.stream.ganglia.tools.model.ToolDefinition;
import java.util.List;

/**
 * Encapsulates all data required for an LLM request.
 */
public record LLMRequest(
    List<Message> messages,
    List<ToolDefinition> tools,
    ModelOptions options
) {}
