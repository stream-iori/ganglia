package work.ganglia.port.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpListToolsResult(
    List<McpTool> tools
) {}
