package work.ganglia.web.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JSON-RPC 2.0 Response model.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JsonRpcResponse(
    String jsonrpc,
    Object id,
    Object result,
    JsonRpcError error
) {
    public record JsonRpcError(int code, String message, Object data) {}

    public static JsonRpcResponse success(Object id, Object result) {
        return new JsonRpcResponse("2.0", id, result, null);
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        return new JsonRpcResponse("2.0", id, null, new JsonRpcError(code, message, null));
    }
}
