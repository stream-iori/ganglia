package work.ganglia.infrastructure.mcp;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.mcp.transport.McpTransport;
import work.ganglia.port.mcp.*;
import work.ganglia.port.mcp.protocol.JsonRpcRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class VertxMcpClient implements McpClient {
    private static final Logger log = LoggerFactory.getLogger(VertxMcpClient.class);

    private final McpTransport transport;
    private final Map<String, Promise<JsonObject>> pendingRequests = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public VertxMcpClient(McpTransport transport) {
        this.transport = transport;
        
        this.transport.messageStream()
            .exceptionHandler(e -> log.error("Transport error", e))
            .handler(this::handleIncomingMessage)
            .endHandler(v -> log.info("Transport ended"));
    }

    private void handleIncomingMessage(JsonObject message) {
        if (message.containsKey("id") && (message.containsKey("result") || message.containsKey("error"))) {
            // It's a Response
            String id = String.valueOf(message.getValue("id"));
            Promise<JsonObject> promise = pendingRequests.remove(id);
            if (promise != null) {
                if (message.containsKey("error")) {
                    JsonObject error = message.getJsonObject("error");
                    promise.fail("JSON-RPC Error " + error.getInteger("code", -1) + ": " + error.getString("message", "Unknown"));
                } else {
                    promise.complete(message);
                }
            } else {
                log.warn("Received response for unknown id: {}", id);
            }
        } else if (message.containsKey("method") && !message.containsKey("id")) {
            // It's a Notification
            log.debug("Received notification: {}", message.getString("method"));
            // TODO: Expose event bus or handler for notifications like "notifications/tools/list_changed"
        }
    }

    private Future<JsonObject> sendRequest(String method, JsonObject params) {
        long idLong = nextId.getAndIncrement();
        String idStr = String.valueOf(idLong);
        JsonObject request = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .put("id", idLong);
            
        if (params != null) {
            request.put("params", params);
        }

        Promise<JsonObject> promise = Promise.promise();
        pendingRequests.put(idStr, promise);

        transport.send(request).onFailure(err -> {
            pendingRequests.remove(idStr);
            promise.fail(err);
        });

        return promise.future();
    }

    private Future<Void> sendNotification(String method, JsonObject params) {
        JsonObject request = new JsonObject()
            .put("jsonrpc", "2.0")
            .put("method", method);
            
        if (params != null) {
            request.put("params", params);
        }

        return transport.send(request);
    }

    @Override
    public Future<McpInitializeResult> initialize(McpInitializeRequest request) {
        JsonObject params = JsonObject.mapFrom(request);
        return sendRequest("initialize", params)
            .map(response -> response.getJsonObject("result").mapTo(McpInitializeResult.class))
            .compose(result -> sendNotification("notifications/initialized", null).map(v -> result));
    }

    @Override
    public Future<McpListToolsResult> listTools() {
        return sendRequest("tools/list", null)
            .map(response -> {
                JsonObject result = response.getJsonObject("result");
                JsonArray toolsArray = result.getJsonArray("tools");
                List<McpTool> tools = new ArrayList<>();
                if (toolsArray != null) {
                    for (int i = 0; i < toolsArray.size(); i++) {
                        tools.add(toolsArray.getJsonObject(i).mapTo(McpTool.class));
                    }
                }
                return new McpListToolsResult(tools);
            });
    }

    @Override
    public Future<McpCallToolResult> callTool(McpCallToolRequest request) {
        JsonObject params = JsonObject.mapFrom(request);
        return sendRequest("tools/call", params)
            .map(response -> response.getJsonObject("result").mapTo(McpCallToolResult.class));
    }

    @Override
    public Future<Void> ping() {
        return sendRequest("ping", null).mapEmpty();
    }

    @Override
    public Future<Void> close() {
        pendingRequests.values().forEach(p -> p.fail("Client closed"));
        pendingRequests.clear();
        return transport.close();
    }
}
