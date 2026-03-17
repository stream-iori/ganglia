package work.ganglia.port.mcp;

import java.util.Map;

/**
 * Represents a tool provided by an MCP server.
 */
public record McpTool(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
