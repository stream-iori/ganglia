package work.ganglia.port.mcp.protocol;

/** A JSON-RPC 2.0 Response object. */
public record JsonRpcResponse(Object result, Object id, String jsonrpc) implements JsonRpcMessage {
  public JsonRpcResponse(Object result, Object id) {
    this(result, id, "2.0");
  }
}
