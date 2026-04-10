package work.ganglia.trajectory.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

  @Test
  void testEventWrittenAsJsonl(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    ObservationEvent event =
        ObservationEvent.of("session-1", ObservationType.SESSION_STARTED, "Hello Agent");
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event));

    vertx.setTimer(
        300,
        id ->
            testContext.verify(
                () -> {
                  File[] files = tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                  assertTrue(files != null && files.length == 1, "JSONL file should be created");
                  // No markdown files should exist
                  File[] mdFiles = tmp.listFiles((d, name) -> name.endsWith(".md"));
                  assertTrue(
                      mdFiles == null || mdFiles.length == 0,
                      "No markdown files should be created");

                  String content = Files.readString(files[0].toPath());
                  assertTrue(content.contains("\"type\":\"SESSION_STARTED\""));
                  assertTrue(content.contains("\"sessionId\":\"session-1\""));
                  assertTrue(content.contains("\"content\":\"Hello Agent\""));
                  testContext.completeNow();
                }));
  }

  @Test
  void testDisabledConfigSkipsWrite(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
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
                  File dir = new File(tracePath);
                  assertTrue(
                      !dir.exists() || dir.listFiles() == null || dir.listFiles().length == 0,
                      "No trace files when disabled");
                  testContext.completeNow();
                }));
  }

  @Test
  void testTokenReceivedIsFiltered(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    // Send a TOKEN_RECEIVED (should be filtered) and a TURN_STARTED (should persist)
    ObservationEvent tokenEvent =
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "streaming chunk");
    ObservationEvent turnEvent =
        ObservationEvent.of("s1", ObservationType.TURN_STARTED, "user input");

    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(tokenEvent));
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(turnEvent));

    vertx.setTimer(
        300,
        id ->
            testContext.verify(
                () -> {
                  File[] files = tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                  assertTrue(files != null && files.length > 0);
                  String content = Files.readString(files[0].toPath());
                  assertTrue(
                      !content.contains("TOKEN_RECEIVED"), "TOKEN_RECEIVED should be filtered out");
                  assertTrue(content.contains("TURN_STARTED"), "TURN_STARTED should be persisted");
                  testContext.completeNow();
                }));
  }

  @Test
  void testMultipleEventsWrittenAsLines(
      Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
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
                  File[] files = tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                  assertTrue(files != null && files.length > 0, "JSONL file should be created");
                  String content = Files.readString(files[0].toPath());

                  // Each event is a separate line
                  String[] lines = content.split("\n");
                  assertEquals(4, lines.length, "Should have 4 JSONL lines");

                  // Verify each line is valid JSON with expected type
                  assertTrue(content.contains("\"type\":\"MODEL_CALL_STARTED\""));
                  assertTrue(content.contains("\"type\":\"MODEL_CALL_FINISHED\""));
                  assertTrue(content.contains("\"type\":\"TOKEN_USAGE_RECORDED\""));
                  assertTrue(content.contains("\"type\":\"MEMORY_UPDATED\""));

                  // Verify data fields are preserved
                  assertTrue(content.contains("\"model\":\"claude-3-5\""));
                  assertTrue(content.contains("\"durationMs\":1234"));
                  assertTrue(content.contains("\"promptTokens\":100"));

                  testContext.completeNow();
                }));
  }

  @Test
  void testCloseUnregistersConsumer(Vertx vertx, VertxTestContext testContext, @TempDir File tmp) {
    TraceManager manager = new TraceManager(vertx, enabledConfig(tmp.getAbsolutePath()));

    // Write an initial event so we know it works before close
    ObservationEvent event1 =
        ObservationEvent.of("s1", ObservationType.SESSION_STARTED, "before close");
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event1));

    vertx.setTimer(
        300,
        id1 -> {
          manager
              .close()
              .onComplete(
                  testContext.succeeding(
                      v -> {
                        // After close, record current file state
                        File[] filesBefore = tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                        String contentBefore;
                        try {
                          contentBefore =
                              filesBefore != null && filesBefore.length > 0
                                  ? Files.readString(filesBefore[0].toPath())
                                  : "";
                        } catch (Exception e) {
                          testContext.failNow(e);
                          return;
                        }

                        // Publish another event after close — should NOT be written
                        ObservationEvent event2 =
                            ObservationEvent.of("s2", ObservationType.TURN_STARTED, "after close");
                        vertx
                            .eventBus()
                            .publish(
                                Constants.ADDRESS_OBSERVATIONS_ALL, JsonObject.mapFrom(event2));

                        vertx.setTimer(
                            300,
                            id2 ->
                                testContext.verify(
                                    () -> {
                                      File[] filesAfter =
                                          tmp.listFiles((d, name) -> name.endsWith(".jsonl"));
                                      String contentAfter =
                                          filesAfter != null && filesAfter.length > 0
                                              ? Files.readString(filesAfter[0].toPath())
                                              : "";
                                      assertFalse(
                                          contentAfter.contains("after close"),
                                          "Events after close() should not be written");
                                      testContext.completeNow();
                                    }));
                      }));
        });
  }
}
