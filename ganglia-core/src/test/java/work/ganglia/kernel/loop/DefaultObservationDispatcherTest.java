package work.ganglia.kernel.loop;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.util.Constants;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class DefaultObservationDispatcherTest {

    private DefaultObservationDispatcher dispatcher;

    @BeforeEach
    void setUp(Vertx vertx) {
        dispatcher = new DefaultObservationDispatcher(vertx);
    }

    @Test
    void shouldPublishToBothTopics(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "test-session";
        String content = "Hello Token";
        ObservationType type = ObservationType.TOKEN_RECEIVED;
        
        AtomicInteger receivedCount = new AtomicInteger(0);

        // 1. Listen to session-specific topic
        vertx.eventBus().<JsonObject>consumer(Constants.ADDRESS_OBSERVATIONS_PREFIX + sessionId, msg -> {
            ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
            assertEquals(content, event.content());
            if (receivedCount.incrementAndGet() == 2) testContext.completeNow();
        });

        // 2. Listen to global topic
        vertx.eventBus().<JsonObject>consumer(Constants.ADDRESS_OBSERVATIONS_ALL, msg -> {
            ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
            assertEquals(content, event.content());
            if (receivedCount.incrementAndGet() == 2) testContext.completeNow();
        });

        dispatcher.dispatch(sessionId, type, content);
    }

    @Test
    void shouldHandleMacroEventsViaObserverInterface(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "macro-session";
        Map<String, Object> data = Map.of("key", "value");
        
        vertx.eventBus().<JsonObject>consumer(Constants.ADDRESS_OBSERVATIONS_ALL, msg -> {
            ObservationEvent event = msg.body().mapTo(ObservationEvent.class);
            testContext.verify(() -> {
                assertEquals(ObservationType.TOOL_STARTED, event.type());
                assertEquals("bash", event.content());
                assertEquals("value", event.data().get("key"));
                testContext.completeNow();
            });
        });

        dispatcher.onObservation(sessionId, ObservationType.TOOL_STARTED, "bash", data);
    }
}
