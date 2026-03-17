package work.ganglia.port.mcp;

import java.util.Map;

public record McpCallToolRequest(
    String name,
    Map<String, Object> arguments
) {}
