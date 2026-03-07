package work.ganglia.web.model;

/**
 * JSON-RPC 2.0 Notification model (server-to-client).
 */
public record JsonRpcNotification(
    String jsonrpc,
    String method,
    Object params
) {
    public static JsonRpcNotification create(String method, Object params) {
        return new JsonRpcNotification("2.0", method, params);
    }
}
