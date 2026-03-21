package work.ganglia.port.mcp.protocol;

/** A JSON-RPC 2.0 Error object. */
public record JsonRpcError(ErrorDetail error, Object id, String jsonrpc) implements JsonRpcMessage {
  public JsonRpcError(ErrorDetail error, Object id) {
    this(error, id, "2.0");
  }

  public record ErrorDetail(int code, String message, Object data) {}
}
