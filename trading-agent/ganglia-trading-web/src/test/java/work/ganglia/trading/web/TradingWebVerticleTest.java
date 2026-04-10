package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;

@ExtendWith(VertxExtension.class)
class TradingWebVerticleTest {

  private TradingWebVerticle verticle;
  private InMemoryBlackboard blackboard;
  private WebClient client;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext testContext) {
    blackboard = new InMemoryBlackboard();
    TradingPipelineOrchestrator orchestrator = mock(TradingPipelineOrchestrator.class);

    verticle = new TradingWebVerticle(0, null, orchestrator, TradingConfig.defaults(), blackboard);

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
  void getConfig_returnsDefaultConfig(Vertx vertx, VertxTestContext testContext) {
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
                        assertEquals(3, config.getInteger("maxDebateRounds"));
                        assertEquals(2, config.getInteger("maxRiskDiscussRounds"));
                        assertEquals("en", config.getString("outputLanguage"));
                        assertEquals("stock", config.getString("instrumentContext"));
                        assertEquals("YFINANCE", config.getString("dataVendor"));
                        assertTrue(config.getBoolean("enableMemoryTwr"));
                        assertEquals(180, config.getInteger("memoryHalfLifeDays"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void getSignals_returnsEmptyArrayInitially(Vertx vertx, VertxTestContext testContext) {
    client
        .get(verticle.getActualPort(), "localhost", "/api/signals")
        .send()
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonArray signals = response.bodyAsJsonArray();
                        assertTrue(signals.isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void putConfig_updatesConfig(Vertx vertx, VertxTestContext testContext) {
    JsonObject newConfig =
        new JsonObject()
            .put("investmentStyle", "GROWTH")
            .put("maxDebateRounds", 5)
            .put("maxRiskDiscussRounds", 3)
            .put("outputLanguage", "zh")
            .put("instrumentContext", "crypto")
            .put("dataVendor", "ALPHA_VANTAGE")
            .put("fallbackVendor", "YFINANCE")
            .put("enableMemoryTwr", false)
            .put("memoryHalfLifeDays", 90);

    client
        .put(verticle.getActualPort(), "localhost", "/api/config")
        .sendJsonObject(newConfig)
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonObject updated = response.bodyAsJsonObject();
                        assertEquals("GROWTH", updated.getString("investmentStyle"));
                        assertEquals(5, updated.getInteger("maxDebateRounds"));

                        // Verify the config persisted by reading it back
                        client
                            .get(verticle.getActualPort(), "localhost", "/api/config")
                            .send()
                            .onComplete(
                                testContext.succeeding(
                                    r2 -> {
                                      testContext.verify(
                                          () -> {
                                            assertEquals(
                                                "GROWTH",
                                                r2.bodyAsJsonObject().getString("investmentStyle"));
                                            testContext.completeNow();
                                          });
                                    }));
                      });
                }));
  }

  @Test
  void getMemory_returnsEmptyWhenNoFacts(Vertx vertx, VertxTestContext testContext) {
    client
        .get(verticle.getActualPort(), "localhost", "/api/memory")
        .send()
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonArray facts = response.bodyAsJsonArray();
                        assertTrue(facts.isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void runPipeline_returnsStartedStatus(Vertx vertx, VertxTestContext testContext) {
    client
        .post(verticle.getActualPort(), "localhost", "/api/pipeline/run")
        .sendJsonObject(new JsonObject().put("ticker", "TSLA"))
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonObject body = response.bodyAsJsonObject();
                        assertEquals("started", body.getString("status"));
                        assertEquals("TSLA", body.getString("ticker"));
                        testContext.completeNow();
                      });
                }));
  }
}
