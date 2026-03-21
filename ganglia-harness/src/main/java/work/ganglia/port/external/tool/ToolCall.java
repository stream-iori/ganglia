package work.ganglia.port.external.tool;

import java.util.Map;

/** Represents a request for the agent to execute a tool. */
public record ToolCall(
    String id, String toolName, Map<String, Object> arguments // Parsed JSON arguments
    ) {}
