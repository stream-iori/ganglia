package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.ganglia.web.model.EventType;
import work.ganglia.web.model.ServerEvent;
import work.ganglia.port.external.tool.ObservationType;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(VertxExtension.class)
public class WebUIEventPublisherTest {

    static Stream<Arguments> observationEventProvider() {
        return Stream.of(
            arguments(ObservationType.REASONING_FINISHED, "Thinking...", null, EventType.THOUGHT, Map.of("content", "Thinking...")),
            arguments(ObservationType.TURN_FINISHED, "Done", null, EventType.AGENT_MESSAGE, Map.of("content", "Done")),
            arguments(ObservationType.TOOL_STARTED, "ls", Map.of("toolCallId", "c1", "command", "ls -l"), EventType.TOOL_START, Map.of("toolName", "ls", "command", "ls -l")),
            arguments(ObservationType.TOOL_FINISHED, "ls", Map.of("toolCallId", "c1", "exitCode", 0, "isError", false), EventType.TOOL_RESULT, Map.of("toolCallId", "c1", "exitCode", 0, "isError", false)),
            arguments(ObservationType.ERROR, "Fail", Map.of("errorCode", "ERR", "canRetry", false), EventType.SYSTEM_ERROR, Map.of("message", "Fail", "code", "ERR", "canRetry", false))
        );
    }

    @ParameterizedTest(name = "Should publish {3} event from {0} observation")
    @MethodSource("observationEventProvider")
    @DisplayName("Parameterized Observation Publishing Test")
    void shouldPublishCorrectEvent(
            ObservationType obsType, 
            String content, 
            Map<String, Object> data, 
            EventType expectedType, 
            Map<String, Object> expectedDataFields,
            Vertx vertx, 
            VertxTestContext testContext) {
        
        String sessionId = "test-session";
        WebUIEventPublisher publisher = new WebUIEventPublisher(vertx);

        vertx.eventBus().<JsonObject>consumer("ganglia.ui.ws.events", message -> {
            testContext.verify(() -> {
                assertEquals(sessionId, message.headers().get("sessionId"));
                ServerEvent event = message.body().mapTo(ServerEvent.class);
                assertEquals(expectedType, event.type());
                
                JsonObject eventData = JsonObject.mapFrom(event.data());
                expectedDataFields.forEach((key, value) -> {
                    assertEquals(value, eventData.getValue(key), "Field mismatch: " + key);
                });
                
                testContext.completeNow();
            });
        });

        publisher.onObservation(sessionId, obsType, content, data);
    }
}
