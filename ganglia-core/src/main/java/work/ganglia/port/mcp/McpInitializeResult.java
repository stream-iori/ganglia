package work.ganglia.port.mcp;

import java.util.Map;

public record McpInitializeResult(
    String protocolVersion,
    Map<String, Object> capabilities,
    Implementation serverInfo
) {
    public record Implementation(
        String name,
        String version
    ) {}
}
