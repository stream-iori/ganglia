package work.ganglia.infrastructure.mcp;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.mcp.McpServer;

/**
 * A lightweight MCP Server implementation strictly for integration testing. Uses WebSockets as the
 * streamable transport.
 */
public class VertxMcpServer implements McpServer {
  private static final Logger log = LoggerFactory.getLogger(VertxMcpServer.class);

  private final Vertx vertx;
  private final int port;
  private HttpServer httpServer;
  private final Map<String, ToolHandler> toolHandlers = new HashMap<>();

  public interface ToolHandler {
    JsonObject handle(JsonObject arguments);
  }

  public VertxMcpServer(Vertx vertx, int port) {
    this.vertx = vertx;
    this.port = port;
  }

  public void registerTool(
      String name, String description, JsonObject inputSchema, ToolHandler handler) {
    JsonObject toolDef =
        new JsonObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", inputSchema);
    toolHandlers.put(name, handler);
    // We could store toolDefs to serve in tools/list
  }

  @Override
  public Future<Void> start() {
    httpServer = vertx.createHttpServer();
    httpServer.webSocketHandler(this::handleWebSocket);
    return httpServer.listen(port).mapEmpty();
  }

  private void handleWebSocket(ServerWebSocket ws) {
    ws.textMessageHandler(
        text -> {
          try {
            JsonObject message = new JsonObject(text);
            if (message.containsKey("method")) {
              String method = message.getString("method");
              Object id = message.getValue("id");

              JsonObject response = new JsonObject().put("jsonrpc", "2.0").put("id", id);

              if ("initialize".equals(method)) {
                response.put(
                    "result",
                    new JsonObject()
                        .put("protocolVersion", "2025-11-25")
                        .put(
                            "capabilities",
                            new JsonObject()
                                .put("tools", new JsonObject())
                                .put("resources", new JsonObject())
                                .put("prompts", new JsonObject()))
                        .put(
                            "serverInfo",
                            new JsonObject().put("name", "TestServer").put("version", "1.0")));
              } else if ("tools/list".equals(method)) {
                // Mock returning a tool
                JsonArray tools = new JsonArray();
                tools.add(
                    new JsonObject()
                        .put("name", "echo")
                        .put("description", "Echoes the input")
                        .put(
                            "inputSchema",
                            new JsonObject()
                                .put("type", "object")
                                .put(
                                    "properties",
                                    new JsonObject()
                                        .put("text", new JsonObject().put("type", "string")))));
                response.put("result", new JsonObject().put("tools", tools));
              } else if ("tools/call".equals(method)) {
                JsonObject params = message.getJsonObject("params");
                String name = params.getString("name");
                JsonObject args = params.getJsonObject("arguments");

                if ("echo".equals(name)) {
                  String textArg = args.getString("text");
                  JsonArray content =
                      new JsonArray()
                          .add(
                              new JsonObject().put("type", "text").put("text", "Echo: " + textArg));
                  response.put(
                      "result", new JsonObject().put("content", content).put("isError", false));
                } else {
                  response.put(
                      "error",
                      new JsonObject().put("code", -32601).put("message", "Tool not found"));
                }
              } else if ("ping".equals(method)) {
                response.put("result", new JsonObject());
              } else if ("notifications/initialized".equals(method)) {
                // Just ack, no response needed for notification
                return;
              }

              ws.writeTextMessage(response.encode());
            }
          } catch (Exception e) {
            log.error("Server processing error", e);
          }
        });
  }

  @Override
  public Future<Void> close() {
    if (httpServer != null) {
      return httpServer.close();
    }
    return Future.succeededFuture();
  }
}
