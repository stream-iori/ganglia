package work.ganglia.port.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

/**
 * Represents a tool provided by an MCP server.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpTool(
    String name,
    String description,
    Map<String, Object> inputSchema
) {}
