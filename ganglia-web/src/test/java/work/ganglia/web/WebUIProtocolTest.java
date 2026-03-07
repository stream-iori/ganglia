package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.port.internal.state.SessionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

@ExtendWith(VertxExtension.class)
public class WebUIProtocolTest {

    @Test
    @DisplayName("Should handle JSON-RPC SYNC request over WebSocket")
    void shouldHandleSyncRpc(Vertx vertx, VertxTestContext testContext) {
        int port = 8890; 
        WebUIVerticle verticle = new WebUIVerticle(port, mock(AgentLoop.class), mock(SessionManager.class));

        vertx.deployVerticle(verticle).onComplete(res -> {
            if (res.failed()) {
                testContext.failNow(res.cause());
                return;
            }

            WebSocketClient client = vertx.createWebSocketClient();
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setPort(port)
                .setHost("127.0.0.1")
                .setURI("/ws");

            vertx.setTimer(500, t -> {
                client.connect(options).onComplete(wsRes -> {
                    if (wsRes.failed()) {
                        testContext.failNow(wsRes.cause());
                        return;
                    }

                    io.vertx.core.http.WebSocket ws = wsRes.result();
                    String sessionId = "test-session-rpc";
                    int requestId = 123;

                    JsonObject request = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("method", "SYNC")
                        .put("params", new JsonObject().put("sessionId", sessionId))
                        .put("id", requestId);

                    ws.textMessageHandler(text -> {
                        JsonObject response = new JsonObject(text);
                        if (response.containsKey("id") && response.getValue("id").equals(requestId)) {
                            testContext.verify(() -> {
                                assertEquals("2.0", response.getString("jsonrpc"));
                                assertNotNull(response.getJsonObject("result"));
                                client.close();
                                testContext.completeNow();
                            });
                        }
                    });

                    ws.writeTextMessage(request.encode());
                });
            });
        });
    }

    @Test
    @DisplayName("Should handle JSON-RPC LIST_FILES request over WebSocket")
    void shouldHandleListFilesRpc(Vertx vertx, VertxTestContext testContext) {
        int port = 8891; 
        WebUIVerticle verticle = new WebUIVerticle(port, mock(AgentLoop.class), mock(SessionManager.class));

        vertx.deployVerticle(verticle).onComplete(res -> {
            if (res.failed()) {
                testContext.failNow(res.cause());
                return;
            }

            WebSocketClient client = vertx.createWebSocketClient();
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setPort(port)
                .setHost("127.0.0.1")
                .setURI("/ws");

            vertx.setTimer(500, t -> {
                client.connect(options).onComplete(wsRes -> {
                    if (wsRes.failed()) {
                        testContext.failNow(wsRes.cause());
                        return;
                    }

                    io.vertx.core.http.WebSocket ws = wsRes.result();
                    String sessionId = "test-session-list-files";
                    int requestId = 456;

                    JsonObject request = new JsonObject()
                        .put("jsonrpc", "2.0")
                        .put("method", "LIST_FILES")
                        .put("params", new JsonObject().put("sessionId", sessionId))
                        .put("id", requestId);

                    ws.textMessageHandler(text -> {
                        JsonObject response = new JsonObject(text);
                        
                        // We might get notifications (server_event) before the response
                        if (response.containsKey("id") && response.getValue("id").equals(requestId)) {
                            testContext.verify(() -> {
                                assertEquals("2.0", response.getString("jsonrpc"));
                                assertEquals("ok", response.getJsonObject("result").getString("status"));
                                client.close();
                                testContext.completeNow();
                            });
                        }
                    });

                    ws.writeTextMessage(request.encode());
                });
            });
        });
    }
}
