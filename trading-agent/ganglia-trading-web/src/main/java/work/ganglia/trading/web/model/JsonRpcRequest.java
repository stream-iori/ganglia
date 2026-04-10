package work.ganglia.trading.web.model;

import io.vertx.core.json.JsonObject;

/** JSON-RPC 2.0 Request model. */
public record JsonRpcRequest(String jsonrpc, String method, JsonObject params, Object id) {
  public boolean isNotification() {
    return id == null;
  }
}
