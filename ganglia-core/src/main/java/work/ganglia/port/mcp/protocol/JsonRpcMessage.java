package work.ganglia.port.mcp.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Base interface for all JSON-RPC 2.0 messages. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
  @JsonSubTypes.Type(value = JsonRpcRequest.class),
  @JsonSubTypes.Type(value = JsonRpcNotification.class),
  @JsonSubTypes.Type(value = JsonRpcResponse.class),
  @JsonSubTypes.Type(value = JsonRpcError.class)
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public interface JsonRpcMessage {
  String jsonrpc = "2.0";

  default String getJsonrpc() {
    return jsonrpc;
  }
}
