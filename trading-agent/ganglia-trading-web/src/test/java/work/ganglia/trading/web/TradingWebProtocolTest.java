package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;

@ExtendWith(VertxExtension.class)
class TradingWebProtocolTest {

  private TradingWebVerticle verticle;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    TradingPipelineOrchestrator orchestrator = mock(TradingPipelineOrchestrator.class);
    // Return a never-completing future so the pipeline doesn't NPE
    when(orchestrator.execute(anyString(), any()))
        .thenReturn(Promise.<TradingPipelineOrchestrator.PipelineResult>promise().future());
    InMemoryBlackboard blackboard = new InMemoryBlackboard();

    verticle = new TradingWebVerticle(0, null, orchestrator, TradingConfig.defaults(), blackboard);

    vertx
        .deployVerticle(verticle)
        .onComplete(testContext.succeeding(id -> testContext.completeNow()));
  }

  @Test
  void sync_returnsHistoryAndInitConfig(Vertx vertx, VertxTestContext testContext) {
    WebSocketClient wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions opts =
        new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(verticle.getActualPort())
            .setURI("/ws");

    wsClient
        .connect(opts)
        .onComplete(
            testContext.succeeding(
                ws -> {
                  AtomicReference<JsonObject> initConfig = new AtomicReference<>();

                  ws.textMessageHandler(
                      text -> {
                        JsonObject msg = new JsonObject(text);
                        String method = msg.getString("method");

                        if ("server_event".equals(method)) {
                          JsonObject params = msg.getJsonObject("params");
                          if ("INIT_CONFIG".equals(params.getString("type"))) {
                            initConfig.set(params);
                          }
                        } else if (msg.containsKey("result")) {
                          // This is the RPC response
                          testContext.verify(
                              () -> {
                                assertNotNull(msg.getValue("result"));
                                JsonObject result = msg.getJsonObject("result");
                                assertNotNull(result.getJsonArray("history"));
                                assertNotNull(
                                    initConfig.get(), "Should have received INIT_CONFIG event");
                                testContext.completeNow();
                              });
                        }
                      });

                  // Send SYNC request
                  JsonObject syncRequest =
                      new JsonObject()
                          .put("jsonrpc", "2.0")
                          .put("method", "SYNC")
                          .put("params", new JsonObject().put("sessionId", "test-session"))
                          .put("id", 1);
                  ws.writeTextMessage(syncRequest.encode());
                }));
  }

  @Test
  void getConfig_pushesConfigEvent(Vertx vertx, VertxTestContext testContext) {
    WebSocketClient wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions opts =
        new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(verticle.getActualPort())
            .setURI("/ws");

    wsClient
        .connect(opts)
        .onComplete(
            testContext.succeeding(
                ws -> {
                  ws.textMessageHandler(
                      text -> {
                        JsonObject msg = new JsonObject(text);
                        if ("server_event".equals(msg.getString("method"))) {
                          JsonObject params = msg.getJsonObject("params");
                          if ("INIT_CONFIG".equals(params.getString("type"))) {
                            testContext.verify(
                                () -> {
                                  JsonObject data = params.getJsonObject("data");
                                  assertNotNull(data.getValue("config"));
                                  testContext.completeNow();
                                });
                          }
                        }
                      });

                  JsonObject request =
                      new JsonObject()
                          .put("jsonrpc", "2.0")
                          .put("method", "GET_CONFIG")
                          .put("params", new JsonObject().put("sessionId", "test-session"))
                          .put("id", 2);
                  ws.writeTextMessage(request.encode());
                }));
  }

  @Test
  void runPipeline_returnsStartedStatus(Vertx vertx, VertxTestContext testContext) {
    WebSocketClient wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions opts =
        new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(verticle.getActualPort())
            .setURI("/ws");

    wsClient
        .connect(opts)
        .onComplete(
            testContext.succeeding(
                ws -> {
                  ws.textMessageHandler(
                      text -> {
                        JsonObject msg = new JsonObject(text);
                        if (msg.containsKey("result")) {
                          testContext.verify(
                              () -> {
                                JsonObject result = msg.getJsonObject("result");
                                assertEquals("started", result.getString("status"));
                                testContext.completeNow();
                              });
                        }
                      });

                  JsonObject request =
                      new JsonObject()
                          .put("jsonrpc", "2.0")
                          .put("method", "RUN_PIPELINE")
                          .put(
                              "params",
                              new JsonObject()
                                  .put("sessionId", "test-session")
                                  .put("ticker", "AAPL"))
                          .put("id", 3);
                  ws.writeTextMessage(request.encode());
                }));
  }

  @Test
  void traceWebSocket_connects(Vertx vertx, VertxTestContext testContext) {
    WebSocketClient wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions opts =
        new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(verticle.getActualPort())
            .setURI("/ws/traces");

    wsClient
        .connect(opts)
        .onComplete(
            testContext.succeeding(
                ws -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(ws);
                        ws.close();
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void invalidWebSocketPath_rejected(Vertx vertx, VertxTestContext testContext) {
    WebSocketClient wsClient = vertx.createWebSocketClient();
    WebSocketConnectOptions opts =
        new WebSocketConnectOptions()
            .setHost("localhost")
            .setPort(verticle.getActualPort())
            .setURI("/invalid");

    wsClient
        .connect(opts)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertNotNull(err);
                        testContext.completeNow();
                      });
                }));
  }
}
