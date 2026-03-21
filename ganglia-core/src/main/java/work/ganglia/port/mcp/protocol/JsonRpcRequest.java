package work.ganglia.port.mcp.protocol;

import java.util.Map;

/** A JSON-RPC 2.0 Request object. */
public record JsonRpcRequest(String method, Map<String, Object> params, Object id, String jsonrpc)
    implements JsonRpcMessage {
  public JsonRpcRequest(String method, Map<String, Object> params, Object id) {
    this(method, params, id, "2.0");
  }
}
