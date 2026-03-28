package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.config.ObservabilityConfigProvider;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.util.Constants;

@ExtendWith(VertxExtension.class)
class TraceManagerTest {

  private ObservabilityConfigProvider enabledConfig(String tracePath) {
    return new ObservabilityConfigProvider() {
      @Override
      public boolean isObservabilityEnabled() {
        return true;
      }

      @Override
      public String getTracePath() {
        return tracePath;
      }
    };
  }

  private ObservabilityConfigProvider disabledConfig() {
    return new ObservabilityConfigProvider() {
      @Override
      public boolean isObservabilityEnabled() {
        return false;
      }

      @Override
      public String getTracePath() {
        return "/tmp/traces";
      }
    };
  }

  @Test
  void testSessionStartedEventWritten(
      Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    ObservationEvent event =
        ObservationEvent.of("session-1", ObservationType.SESSION_STARTED, "Hello Agent");
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event));

    vertx.setTimer(
        300,
        id ->
            testContext.verify(
                () -> {
                  File[] files = tmp.listFiles();
                  assertTrue(files != null && files.length > 0, "Trace file should be created");
                  testContext.completeNow();
                }));
  }

  @Test
  void testDisabledConfigSkipsWrite(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    // Use a specific temp dir path that would only be used if writing occurs
    String tracePath = tmp.getAbsolutePath() + "/disabled-traces";
    new TraceManager(
        vertx,
        new ObservabilityConfigProvider() {
          @Override
          public boolean isObservabilityEnabled() {
            return false;
          }

          @Override
          public String getTracePath() {
            return tracePath;
          }
        });

    ObservationEvent event =
        ObservationEvent.of("session-2", ObservationType.TURN_STARTED, "Start");
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event));

    vertx.setTimer(
        300,
        id ->
            testContext.verify(
                () -> {
                  // With observability disabled, no trace file should be created
                  File dir = new File(tracePath);
                  assertTrue(
                      !dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0,
                      "No trace files when disabled");
                  testContext.completeNow();
                }));
  }

  @Test
  void testMultipleEventTypes(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    ObservationType[] types = {
      ObservationType.TURN_STARTED,
      ObservationType.REASONING_STARTED,
      ObservationType.REQUEST_PREPARED,
      ObservationType.REASONING_FINISHED,
      ObservationType.TOOL_STARTED,
      ObservationType.TOOL_FINISHED,
      ObservationType.TURN_FINISHED,
      ObservationType.SESSION_ENDED,
      ObservationType.ERROR,
      ObservationType.TOKEN_RECEIVED,
    };

    for (ObservationType type : types) {
      Map<String, Object> data =
          switch (type) {
            case REQUEST_PREPARED ->
                Map.of("messageCount", 3, "toolCount", 2, "model", "claude-3-5");
            case TURN_STARTED, TURN_FINISHED -> Map.of("turnNumber", 1);
            case SESSION_ENDED -> Map.of("durationMs", 5000L);
            case ERROR -> Map.of("cause", "timeout");
            default -> null;
          };

      ObservationEvent event;
      if (data != null) {
        event = ObservationEvent.of("s1", type, "content", data);
      } else {
        event = ObservationEvent.of("s1", type, "content");
      }
      vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event));
    }

    vertx.setTimer(
        500,
        id ->
            testContext.verify(
                () -> {
                  File[] files = tmp.listFiles();
                  assertTrue(files != null && files.length > 0);
                  testContext.completeNow();
                }));
  }

  @Test
  void testNewObservabilityTypes(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    ObservationEvent[] events = {
      ObservationEvent.of(
          "s1",
          ObservationType.MODEL_CALL_STARTED,
          "claude-3-5",
          Map.of("model", "claude-3-5", "attempt", 1, "streaming", true)),
      ObservationEvent.of(
          "s1",
          ObservationType.MODEL_CALL_FINISHED,
          "claude-3-5",
          Map.of("model", "claude-3-5", "attempt", 1, "durationMs", 1234L, "status", "success")),
      ObservationEvent.of(
          "s1",
          ObservationType.TOKEN_USAGE_RECORDED,
          null,
          Map.of("promptTokens", 100, "completionTokens", 50, "totalTokens", 150)),
      ObservationEvent.of(
          "s1",
          ObservationType.MEMORY_UPDATED,
          "TURN_COMPLETED",
          Map.of("memoryEventType", "TURN_COMPLETED", "moduleCount", 2))
    };

    // Stagger events to avoid file write races in TraceManager
    for (int i = 0; i < events.length; i++) {
      final ObservationEvent event = events[i];
      vertx.setTimer(
          (long) (i + 1) * 200,
          id ->
              vertx
                  .eventBus()
                  .publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event)));
    }

    vertx.setTimer(
        2000,
        id ->
            testContext.verify(
                () -> {
                  File[] files = tmp.listFiles((d, name) -> name.endsWith(".md"));
                  assertTrue(files != null && files.length > 0, "Trace file should be created");
                  String content = Files.readString(files[0].toPath());
                  assertTrue(
                      content.contains("**API Call**") && content.contains("claude-3-5"),
                      "Should contain MODEL_CALL_STARTED with model name");
                  assertTrue(
                      content.contains("**API Call Finished**") && content.contains("1234ms"),
                      "Should contain MODEL_CALL_FINISHED with duration");
                  assertTrue(
                      content.contains("**Usage:**")
                          && content.contains("prompt=100")
                          && content.contains("completion=50")
                          && content.contains("total=150"),
                      "Should contain TOKEN_USAGE_RECORDED with token counts");
                  assertTrue(
                      content.contains("_Memory updated:_") && content.contains("TURN_COMPLETED"),
                      "Should contain MEMORY_UPDATED with event type");

                  // Verify JSONL file
                  File[] jsonlFiles = tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                  assertTrue(
                      jsonlFiles != null && jsonlFiles.length > 0, "JSONL file should be created");
                  String jsonlContent = Files.readString(jsonlFiles[0].toPath());
                  assertTrue(
                      jsonlContent.contains("\"type\":\"MODEL_CALL_STARTED\""),
                      "JSONL should contain event type");
                  assertTrue(
                      jsonlContent.contains("\"model\":\"claude-3-5\""),
                      "JSONL should contain model name");

                  testContext.completeNow();
                }));
  }
}
