package work.ganglia.port.mcp.protocol;

import java.util.Map;

/**
 * A JSON-RPC 2.0 Notification object.
 */
public record JsonRpcNotification(
    String method,
    Map<String, Object> params,
    String jsonrpc
) implements JsonRpcMessage {
    public JsonRpcNotification(String method, Map<String, Object> params) {
        this(method, params, "2.0");
    }
}
