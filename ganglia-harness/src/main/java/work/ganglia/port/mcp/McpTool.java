package work.ganglia.port.mcp;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Represents a tool provided by an MCP server. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record McpTool(String name, String description, Map<String, Object> inputSchema) {}
