package work.ganglia.kernel.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.util.Constants;

public class DefaultObservationDispatcherTest extends BaseGangliaTest {

  private DefaultObservationDispatcher dispatcher;

  @BeforeEach
  void setUpDispatcher() {
    dispatcher = new DefaultObservationDispatcher(vertx);
  }

  @Test
  void shouldPublishToBothTopics(VertxTestContext testContext) {
    String sessionId = "test-session";
    String content = "Hello Token";
    ObservationType type = ObservationType.TOKEN_RECEIVED;

    AtomicInteger receivedCount = new AtomicInteger(0);

    // 1. Listen to session-specific topic
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_PREFIX + sessionId,
            msg -> {
              ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
              assertEquals(content, event.content());
              if (receivedCount.incrementAndGet() == 2) testContext.completeNow();
            });

    // 2. Listen to global topic
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
              assertEquals(content, event.content());
              if (receivedCount.incrementAndGet() == 2) testContext.completeNow();
            });

    dispatcher.dispatch(sessionId, type, content);
  }

  @Test
  void shouldHandleMacroEventsViaObserverInterface(VertxTestContext testContext) {
    String sessionId = "macro-session";
    Map<String, Object> data = Map.of("key", "value");

    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
              testContext.verify(
                  () -> {
                    assertEquals(ObservationType.TOOL_STARTED, event.type());
                    assertEquals("bash", event.content());
                    assertEquals("value", event.data().get("key"));
                    testContext.completeNow();
                  });
            });

    dispatcher.onObservation(sessionId, ObservationType.TOOL_STARTED, "bash", data);
  }
}
