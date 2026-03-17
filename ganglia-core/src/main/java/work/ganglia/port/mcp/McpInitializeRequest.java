package work.ganglia.port.mcp;

import java.util.Map;

public record McpInitializeRequest(
    String protocolVersion,
    Map<String, Object> capabilities,
    Implementation clientInfo
) {
    public record Implementation(
        String name,
        String version
    ) {}
}
