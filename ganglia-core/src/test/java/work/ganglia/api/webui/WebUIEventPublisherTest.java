package work.ganglia.api.webui;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.api.webui.model.EventType;
import work.ganglia.api.webui.model.ServerEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
public class WebUIEventPublisherTest {

    @Test
    @DisplayName("Should publish THOUGHT event when reasoning is finished")
    void shouldPublishThought(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "test-session";
        WebUIEventPublisher publisher = new WebUIEventPublisher(vertx);

        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream." + sessionId, message -> {
            testContext.verify(() -> {
                ServerEvent event = message.body().mapTo(ServerEvent.class);
                assertEquals(EventType.THOUGHT, event.type());
                
                JsonObject data = JsonObject.mapFrom(event.data());
                assertEquals("I am thinking", data.getString("content"));
                
                testContext.completeNow();
            });
        });

        publisher.onObservation(sessionId, ObservationType.REASONING_FINISHED, "I am thinking", null);
    }

    @Test
    @DisplayName("Should publish TOOL_START event when tool starts")
    void shouldPublishToolStart(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "test-session";
        WebUIEventPublisher publisher = new WebUIEventPublisher(vertx);

        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream." + sessionId, message -> {
            testContext.verify(() -> {
                ServerEvent event = message.body().mapTo(ServerEvent.class);
                assertEquals(EventType.TOOL_START, event.type());
                
                JsonObject data = JsonObject.mapFrom(event.data());
                assertEquals("BashTools", data.getString("toolName"));
                
                testContext.completeNow();
            });
        });

        publisher.onObservation(sessionId, ObservationType.TOOL_STARTED, "BashTools", null);
    }

    @Test
    @DisplayName("Should publish SYSTEM_ERROR event when error occurs")
    void shouldPublishSystemError(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "test-session";
        WebUIEventPublisher publisher = new WebUIEventPublisher(vertx);

        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream." + sessionId, message -> {
            testContext.verify(() -> {
                ServerEvent event = message.body().mapTo(ServerEvent.class);
                assertEquals(EventType.SYSTEM_ERROR, event.type());
                
                JsonObject data = JsonObject.mapFrom(event.data());
                assertEquals("Something went wrong", data.getString("message"));
                assertEquals("LOOP_ERROR", data.getString("code"));
                
                testContext.completeNow();
            });
        });

        publisher.onObservation(sessionId, ObservationType.ERROR, "Something went wrong", null);
    }

    @Test
    @DisplayName("Should publish AGENT_MESSAGE event when turn is finished")
    void shouldPublishAgentMessage(Vertx vertx, VertxTestContext testContext) {
        String sessionId = "test-session";
        WebUIEventPublisher publisher = new WebUIEventPublisher(vertx);

        vertx.eventBus().<JsonObject>consumer("ganglia.ui.stream." + sessionId, message -> {
            testContext.verify(() -> {
                ServerEvent event = message.body().mapTo(ServerEvent.class);
                assertEquals(EventType.AGENT_MESSAGE, event.type());
                
                JsonObject data = JsonObject.mapFrom(event.data());
                assertEquals("Final Answer", data.getString("content"));
                
                testContext.completeNow();
            });
        });

        publisher.onObservation(sessionId, ObservationType.TURN_FINISHED, "Final Answer", null);
    }
}
