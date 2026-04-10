package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.external.tool.ObservationType;

@ExtendWith(VertxExtension.class)
class TradingEventPublisherTest {

  private TradingEventPublisher publisher;

  @BeforeEach
  void setUp(Vertx vertx) {
    publisher = new TradingEventPublisher(vertx);
  }

  @Test
  void managerCycleStarted_mapsToDebateCycleStarted(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("DEBATE_CYCLE_STARTED", event.getString("type"));
                    JsonObject data = event.getJsonObject("data");
                    assertEquals(2, data.getInteger("cycleNumber"));
                    assertEquals(5, data.getInteger("maxCycles"));
                    assertEquals("RESEARCH", data.getString("debateType"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.MANAGER_CYCLE_STARTED,
        "Cycle started",
        Map.of("cycleNumber", 2, "maxCycles", 5, "debateType", "RESEARCH"));
  }

  @Test
  void managerCycleFinished_mapsToDebateCycleFinished(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("DEBATE_CYCLE_FINISHED", event.getString("type"));
                    JsonObject data = event.getJsonObject("data");
                    assertEquals("CONVERGED", data.getString("decisionType"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.MANAGER_CYCLE_FINISHED,
        "Cycle finished",
        Map.of(
            "cycleNumber",
            2,
            "maxCycles",
            5,
            "decisionType",
            "CONVERGED",
            "debateType",
            "RESEARCH"));
  }

  @Test
  void managerGraphConverged_mapsToDebateConverged(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("DEBATE_CONVERGED", event.getString("type"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.MANAGER_GRAPH_CONVERGED,
        "Converged",
        Map.of("totalCycles", 3));
  }

  @Test
  void managerGraphStalled_mapsToDebateStalled(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("DEBATE_STALLED", event.getString("type"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session", ObservationType.MANAGER_GRAPH_STALLED, "No progress detected", Map.of());
  }

  @Test
  void factPublished_mapsToFactPublished(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("FACT_PUBLISHED", event.getString("type"));
                    JsonObject data = event.getJsonObject("data");
                    assertEquals("fact-1", data.getString("factId"));
                    assertEquals("bull", data.getString("sourceManager"));
                    assertEquals(1, data.getInteger("cycleNumber"));
                    assertEquals("bullish", data.getJsonObject("tags").getString("stance"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.FACT_PUBLISHED,
        "Bullish momentum detected",
        Map.of(
            "factId",
            "fact-1",
            "sourceManager",
            "bull",
            "cycleNumber",
            1,
            "tags",
            Map.of("role", "bull", "stance", "bullish")));
  }

  @Test
  void factSuperseded_mapsToFactSuperseded(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("FACT_SUPERSEDED", event.getString("type"));
                    JsonObject data = event.getJsonObject("data");
                    assertEquals("fact-1", data.getString("factId"));
                    assertEquals("Updated analysis", data.getString("reason"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.FACT_SUPERSEDED,
        "Superseded",
        Map.of("factId", "fact-1", "reason", "Updated analysis"));
  }

  @Test
  void tokenReceived_mapsToToken(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("TOKEN", event.getString("type"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation("test-session", ObservationType.TOKEN_RECEIVED, "hello", Map.of());
  }

  @Test
  void toolStarted_mapsToToolStart(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("TOOL_START", event.getString("type"));
                    JsonObject data = event.getJsonObject("data");
                    assertEquals("tc-1", data.getString("toolCallId"));
                    assertEquals("get_stock_data", data.getString("toolName"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.TOOL_STARTED,
        "get_stock_data",
        Map.of("toolCallId", "tc-1", "command", "get_stock_data AAPL"));
  }

  @Test
  void error_mapsToPipelineError(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_EVENTS,
            msg -> {
              testContext.verify(
                  () -> {
                    JsonObject event = msg.body();
                    assertEquals("PIPELINE_ERROR", event.getString("type"));
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.ERROR,
        "Something failed",
        Map.of("errorCode", "LLM_ERROR"));
  }

  @Test
  void cacheAddressReceivesEvents(Vertx vertx, VertxTestContext testContext) {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            TradingEventPublisher.ADDRESS_CACHE,
            msg -> {
              testContext.verify(
                  () -> {
                    assertNotNull(msg.body());
                    testContext.completeNow();
                  });
            });

    publisher.onObservation(
        "test-session",
        ObservationType.MANAGER_GRAPH_CONVERGED,
        "Converged",
        Map.of("totalCycles", 2));
  }

  @Test
  void tokenReceived_notCached(Vertx vertx, VertxTestContext testContext) throws Exception {
    CountDownLatch cacheLatch = new CountDownLatch(1);

    vertx
        .eventBus()
        .<JsonObject>consumer(TradingEventPublisher.ADDRESS_CACHE, msg -> cacheLatch.countDown());

    publisher.onObservation("test-session", ObservationType.TOKEN_RECEIVED, "token", Map.of());

    // Give time for potential cache message
    assertFalse(cacheLatch.await(200, TimeUnit.MILLISECONDS), "Token events should not be cached");
    testContext.completeNow();
  }
}
