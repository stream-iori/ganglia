package work.ganglia.port.mcp;

import java.util.List;

public record McpListToolsResult(
    List<McpTool> tools
) {}
