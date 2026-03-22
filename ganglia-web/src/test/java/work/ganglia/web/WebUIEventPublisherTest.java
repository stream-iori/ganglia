package work.ganglia.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.web.model.EventType;
import work.ganglia.web.model.ServerEvent;

@ExtendWith(VertxExtension.class)
public class WebUiEventPublisherTest {

  static Stream<Arguments> observationEventProvider() {
    return Stream.of(
        arguments(
            ObservationType.REASONING_FINISHED,
            "Thinking...",
            null,
            EventType.THOUGHT,
            Map.of("content", "Thinking...")),
        arguments(
            ObservationType.TURN_FINISHED,
            "Done",
            null,
            EventType.AGENT_MESSAGE,
            Map.of("content", "Done")),
        arguments(
            ObservationType.TOOL_STARTED,
            "ls",
            Map.of("toolCallId", "c1", "command", "ls -l"),
            EventType.TOOL_START,
            Map.of("toolName", "ls", "command", "ls -l")),
        arguments(
            ObservationType.TOOL_FINISHED,
            "ls",
            Map.of("toolCallId", "c1", "exitCode", 0, "isError", false),
            EventType.TOOL_RESULT,
            Map.of("toolCallId", "c1", "exitCode", 0, "isError", false)),
        arguments(
            ObservationType.ERROR,
            "Fail",
            Map.of("errorCode", "ERR", "canRetry", false),
            EventType.SYSTEM_ERROR,
            Map.of("message", "Fail", "code", "ERR", "canRetry", false)));
  }

  @ParameterizedTest(name = "Should publish {3} event from {0} observation via EventBus")
  @MethodSource("observationEventProvider")
  @DisplayName("Unified Stream EventBus Ingestion Test")
  void shouldPublishCorrectEventViaBus(
      ObservationType obsType,
      String content,
      Map<String, Object> data,
      EventType expectedType,
      Map<String, Object> expectedDataFields,
      Vertx vertx,
      VertxTestContext testContext) {

    String sessionId = "test-session";
    // Instantiate publisher - it will register its consumer
    new WebUiEventPublisher(vertx);

    vertx
        .eventBus()
        .<JsonObject>consumer(
            "ganglia.ui.ws.events",
            message -> {
              testContext.verify(
                  () -> {
                    assertEquals(sessionId, message.headers().get("sessionId"));
                    ServerEvent event = message.body().mapTo(ServerEvent.class);
                    assertEquals(expectedType, event.type());

                    JsonObject eventData = JsonObject.mapFrom(event.data());
                    expectedDataFields.forEach(
                        (key, value) -> {
                          assertEquals(value, eventData.getValue(key), "Field mismatch: " + key);
                        });

                    testContext.completeNow();
                  });
            });

    ObservationEvent obs = ObservationEvent.of(sessionId, obsType, content, data);
    vertx
        .eventBus()
        .publish(work.ganglia.util.Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(obs));
  }

  @Test
  @DisplayName("Streaming Token Forwarding Test")
  void shouldStreamTokensToWebUI(Vertx vertx, VertxTestContext testContext) {
    String sessionId = "stream-session";
    String token = "part of a word";

    new WebUiEventPublisher(vertx);

    vertx
        .eventBus()
        .<JsonObject>consumer(
            "ganglia.ui.ws.events",
            message -> {
              testContext.verify(
                  () -> {
                    ServerEvent event = message.body().mapTo(ServerEvent.class);
                    if (event.type() == EventType.TOKEN) {
                      assertEquals(token, ((Map) event.data()).get("content"));
                      testContext.completeNow();
                    }
                  });
            });

    ObservationEvent tokenObs =
        ObservationEvent.of(sessionId, ObservationType.TOKEN_RECEIVED, token);
    vertx
        .eventBus()
        .publish(
            work.ganglia.util.Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(tokenObs));
  }
}
