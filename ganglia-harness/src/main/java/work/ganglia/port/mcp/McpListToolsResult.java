package work.ganglia.port.mcp;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpListToolsResult(List<McpTool> tools) {}
