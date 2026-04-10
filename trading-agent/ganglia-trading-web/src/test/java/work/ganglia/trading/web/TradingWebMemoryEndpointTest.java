package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

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
class TradingWebMemoryEndpointTest {

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
  void getMemory_withFacts_returnsList(Vertx vertx, VertxTestContext testContext) {
    blackboard
        .publish(
            "bull",
            "Strong momentum detected",
            null,
            1,
            Map.of("role", "bull", "stance", "bullish"))
        .compose(
            f1 ->
                blackboard.publish(
                    "bear",
                    "Overvaluation risk",
                    null,
                    1,
                    Map.of("role", "bear", "stance", "bearish")))
        .compose(f2 -> client.get(verticle.getActualPort(), "localhost", "/api/memory").send())
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonArray facts = response.bodyAsJsonArray();
                        assertEquals(2, facts.size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void getMemory_filteredByRole_returnsMatchingFacts(Vertx vertx, VertxTestContext testContext) {
    blackboard
        .publish("bull", "Bullish signal", null, 1, Map.of("role", "bull"))
        .compose(f -> blackboard.publish("bear", "Bearish signal", null, 1, Map.of("role", "bear")))
        .compose(
            f ->
                client
                    .get(verticle.getActualPort(), "localhost", "/api/memory")
                    .addQueryParam("role", "bull")
                    .send())
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(200, response.statusCode());
                        JsonArray facts = response.bodyAsJsonArray();
                        assertEquals(1, facts.size());
                        JsonObject fact = facts.getJsonObject(0);
                        assertEquals("bull", fact.getString("sourceManager"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void getMemory_emptyBlackboard_returnsEmptyArray(Vertx vertx, VertxTestContext testContext) {
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
  void getFactDetail_nonexistent_returns404(Vertx vertx, VertxTestContext testContext) {
    client
        .get(verticle.getActualPort(), "localhost", "/api/memory/nonexistent-id")
        .send()
        .onComplete(
            testContext.succeeding(
                response -> {
                  testContext.verify(
                      () -> {
                        assertEquals(404, response.statusCode());
                        testContext.completeNow();
                      });
                }));
  }
}
