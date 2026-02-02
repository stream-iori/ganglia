package me.stream.ganglia.core.model;

import java.util.Map;

/**
 * Represents a request for the agent to execute a tool.
 */
public record ToolCall(
    String id,
    String toolName,
    Map<String, Object> arguments // Parsed JSON arguments
) {}
