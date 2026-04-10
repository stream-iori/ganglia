package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.trading.signal.SignalExtractor;

/**
 * Integration test for TradingWebVerticle + TradingPipelineOrchestrator with stub agent loop.
 * Verifies the full request flow: REST/WS → pipeline execution → event propagation.
 */
@ExtendWith(VertxExtension.class)
class TradingWebIntegrationTest {

  private static final TradingConfig FAST_CONFIG =
      new TradingConfig(
          TradingConfig.InvestmentStyle.VALUE,
          2,
          1,
          "en",
          "stock",
          TradingConfig.DataVendor.YFINANCE,
          TradingConfig.DataVendor.ALPHA_VANTAGE,
          false,
          180,
          ".ganglia/trading-cache");

  private TradingWebVerticle verticle;
  private TradingEventPublisher eventPublisher;
  private WebClient client;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    InMemoryBlackboard blackboard = new InMemoryBlackboard();
    eventPublisher = new TradingEventPublisher(vertx);

    ObservationDispatcher dispatcher =
        new ObservationDispatcher() {
          @Override
          public void dispatch(String sid, ObservationType type, String content) {
            eventPublisher.onObservation(sid, type, content, Map.of());
          }

          @Override
          public void dispatch(
              String sid, ObservationType type, String content, Map<String, Object> data) {
            eventPublisher.onObservation(sid, type, content, data);
          }
        };

    TradingPipelineOrchestrator orchestrator =
        new TradingPipelineOrchestrator(
            FAST_CONFIG, smartStubLoopFactory(), dispatcher, new SignalExtractor());

    verticle = new TradingWebVerticle(0, null, orchestrator, FAST_CONFIG, blackboard);

    vertx
        .deployVerticle(verticle)
        .onComplete(
            testContext.succeeding(
                id -> {
                  client = WebClient.create(vertx);
                  testContext.completeNow();
                }));
  }

  @Test
  void sync_returnsHistoryAndConfig(Vertx vertx, VertxTestContext testContext) {
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
                          testContext.verify(
                              () -> {
                                JsonObject result = msg.getJsonObject("result");
                                assertNotNull(result.getJsonArray("history"));
                                assertNotNull(initConfig.get(), "Should have received INIT_CONFIG");
                                testContext.completeNow();
                              });
                        }
                      });

                  ws.writeTextMessage(
                      new JsonObject()
                          .put("jsonrpc", "2.0")
                          .put("method", "SYNC")
                          .put("params", new JsonObject().put("sessionId", "it-session"))
                          .put("id", 1)
                          .encode());
                }));
  }

  @Test
  void getConfig_returnsValidConfig(Vertx vertx, VertxTestContext testContext) {
    client
        .get(verticle.getActualPort(), "localhost", "/api/config")
        .send()
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonObject config = response.bodyAsJsonObject();
                        assertEquals("VALUE", config.getString("investmentStyle"));
                        assertEquals(2, config.getInteger("maxDebateRounds"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void updateConfig_persistsChange(Vertx vertx, VertxTestContext testContext) {
    JsonObject newConfig =
        new JsonObject()
            .put("investmentStyle", "GROWTH")
            .put("maxDebateRounds", 5)
            .put("maxRiskDiscussRounds", 3)
            .put("outputLanguage", "zh")
            .put("instrumentContext", "crypto")
            .put("dataVendor", "ALPHA_VANTAGE")
            .put("enableMemoryTwr", false)
            .put("memoryHalfLifeDays", 90);

    client
        .put(verticle.getActualPort(), "localhost", "/api/config")
        .sendJsonObject(newConfig)
        .compose(
            response -> {
              assertEquals(200, response.statusCode());
              return client.get(verticle.getActualPort(), "localhost", "/api/config").send();
            })
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(
                            "GROWTH", response.bodyAsJsonObject().getString("investmentStyle"));
                        assertEquals(5, response.bodyAsJsonObject().getInteger("maxDebateRounds"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void runPipeline_restEndpoint_returnsStarted(Vertx vertx, VertxTestContext testContext) {
    client
        .post(verticle.getActualPort(), "localhost", "/api/pipeline/run")
        .sendJsonObject(new JsonObject().put("ticker", "AAPL"))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        assertEquals("started", response.bodyAsJsonObject().getString("status"));
                        assertEquals("AAPL", response.bodyAsJsonObject().getString("ticker"));
                        testContext.completeNow();
                      });
                }));
  }

  // --- Inline Stub Helpers ---

  private static AgentLoopFactory smartStubLoopFactory() {
    return () ->
        new AgentLoop() {
          @Override
          public Future<String> run(String userInput, SessionContext ctx, AgentSignal signal) {
            String taskLine = extractTaskLine(userInput);

            if (taskLine.contains("portfolio risk recommendation")) {
              return Future.succeededFuture(
                  "**Final Verdict: BUY**\n**Confidence: 0.85**\nRationale: Strong fundamentals.");
            }
            if (taskLine.contains("aggressive risk")) {
              return Future.succeededFuture("Aggressive: High upside potential justifies risk.");
            }
            if (taskLine.contains("balanced risk")) {
              return Future.succeededFuture("Balanced: Risk-reward ratio is favorable.");
            }
            if (taskLine.contains("conservative risk")) {
              return Future.succeededFuture("Conservative: Capital preservation concerns noted.");
            }
            if (taskLine.contains("bullish")) {
              return Future.succeededFuture("Bull case: Strong momentum and growth trajectory.");
            }
            if (taskLine.contains("bearish")) {
              return Future.succeededFuture("Bear case: Overvaluation risks exist.");
            }
            if (taskLine.contains("investment verdict")) {
              return Future.succeededFuture("Investment verdict: Bullish with moderate risk.");
            }
            if (taskLine.contains("market data")) {
              return Future.succeededFuture("Market analysis: Bullish momentum detected.");
            }
            if (taskLine.contains("fundamental data")) {
              return Future.succeededFuture("Fundamentals: Strong earnings growth.");
            }
            if (taskLine.contains("news")) {
              return Future.succeededFuture("News: Positive press coverage.");
            }
            if (taskLine.contains("social media")) {
              return Future.succeededFuture("Social: Positive community sentiment.");
            }
            if (taskLine.contains("aggregate and synthesize")) {
              return Future.succeededFuture("Perception summary: Overall bullish outlook.");
            }
            return Future.succeededFuture("Generic analysis result.");
          }

          @Override
          public Future<String> resume(
              String askId, String toolOutput, SessionContext ctx, AgentSignal signal) {
            return Future.succeededFuture("resumed");
          }

          @Override
          public void stop(String sessionId) {}
        };
  }

  private static String extractTaskLine(String userInput) {
    for (String line : userInput.split("\n")) {
      if (line.startsWith("TASK: ")) {
        return line.substring(6).toLowerCase();
      }
    }
    return userInput.toLowerCase();
  }
}
