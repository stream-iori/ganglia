package work.ganglia.observability.metrics;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;

@ExtendWith(VertxExtension.class)
class MetricAggregatorTest {

  private MetricAggregator aggregator;

  @BeforeEach
  void setUp(Vertx vertx) {
    aggregator = new MetricAggregator(vertx);
  }

  @Test
  void emptySnapshot() {
    JsonObject snapshot = aggregator.snapshot();
    assertNotNull(snapshot.getJsonObject("counters"));
    assertNotNull(snapshot.getJsonObject("histograms"));
    assertTrue(snapshot.getJsonObject("counters").isEmpty());
    assertTrue(snapshot.getJsonObject("histograms").isEmpty());
  }

  @Test
  void modelCallLatency() {
    long now = System.currentTimeMillis();

    aggregator.processEvent(
        new JsonObject()
            .put("type", "MODEL_CALL_STARTED")
            .put("timestamp", now)
            .put("spanId", "span-1"));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "MODEL_CALL_FINISHED")
            .put("timestamp", now + 500)
            .put("spanId", "span-1")
            .put("data", new JsonObject().put("model", "claude-3")));

    JsonObject histograms = aggregator.snapshot().getJsonObject("histograms");
    assertNotNull(histograms.getJsonObject("MODEL_CALL_LATENCY_MS"));
    assertEquals(500.0, histograms.getJsonObject("MODEL_CALL_LATENCY_MS").getDouble("avg"));

    assertNotNull(histograms.getJsonObject("MODEL_CALL_LATENCY_MS.claude-3"));
    assertEquals(
        500.0, histograms.getJsonObject("MODEL_CALL_LATENCY_MS.claude-3").getDouble("avg"));
  }

  @Test
  void ttftTracking() {
    long now = System.currentTimeMillis();

    aggregator.processEvent(
        new JsonObject()
            .put("type", "MODEL_CALL_STARTED")
            .put("timestamp", now)
            .put("spanId", "span-2"));

    // First token
    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOKEN_RECEIVED")
            .put("timestamp", now + 200)
            .put("spanId", "span-2"));

    // Second token should NOT create another TTFT entry
    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOKEN_RECEIVED")
            .put("timestamp", now + 300)
            .put("spanId", "span-2"));

    JsonObject ttft =
        aggregator.snapshot().getJsonObject("histograms").getJsonObject("MODEL_CALL_TTFT_MS");
    assertNotNull(ttft);
    assertEquals(1L, ttft.getLong("count"));
    assertEquals(200.0, ttft.getDouble("avg"));
  }

  @Test
  void tokenUsageCounting() {
    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOKEN_USAGE_RECORDED")
            .put("data", new JsonObject().put("promptTokens", 100).put("completionTokens", 50)));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOKEN_USAGE_RECORDED")
            .put("data", new JsonObject().put("promptTokens", 200).put("completionTokens", 75)));

    JsonObject counters = aggregator.snapshot().getJsonObject("counters");
    assertEquals(300L, counters.getLong("TOKEN_PROMPT_TOTAL"));
    assertEquals(125L, counters.getLong("TOKEN_COMPLETION_TOTAL"));
  }

  @Test
  void toolDurationAndSuccessFailure() {
    long now = System.currentTimeMillis();

    aggregator.processEvent(
        new JsonObject().put("type", "TOOL_STARTED").put("timestamp", now).put("spanId", "t1"));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOOL_FINISHED")
            .put("timestamp", now + 300)
            .put("spanId", "t1")
            .put("data", new JsonObject().put("toolName", "bash").put("isError", false)));

    aggregator.processEvent(
        new JsonObject().put("type", "TOOL_STARTED").put("timestamp", now).put("spanId", "t2"));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "TOOL_FINISHED")
            .put("timestamp", now + 100)
            .put("spanId", "t2")
            .put("data", new JsonObject().put("toolName", "bash").put("isError", true)));

    JsonObject snapshot = aggregator.snapshot();
    JsonObject counters = snapshot.getJsonObject("counters");
    assertEquals(1L, counters.getLong("TOOL_SUCCESS_COUNT"));
    assertEquals(1L, counters.getLong("TOOL_FAILURE_COUNT"));

    JsonObject histograms = snapshot.getJsonObject("histograms");
    assertNotNull(histograms.getJsonObject("TOOL_EXECUTION_DURATION_MS.bash"));
    assertEquals(2L, histograms.getJsonObject("TOOL_EXECUTION_DURATION_MS").getLong("count"));
  }

  @Test
  void contextCompressionAndSessionAbort() {
    aggregator.processEvent(new JsonObject().put("type", "CONTEXT_COMPRESSED"));
    aggregator.processEvent(new JsonObject().put("type", "CONTEXT_COMPRESSED"));
    aggregator.processEvent(new JsonObject().put("type", "SESSION_ABORTED"));

    JsonObject counters = aggregator.snapshot().getJsonObject("counters");
    assertEquals(2L, counters.getLong("CONTEXT_COMPRESSION_COUNT"));
    assertEquals(1L, counters.getLong("SESSION_ABORT_COUNT"));
  }

  @Test
  void errorCountByCode() {
    aggregator.processEvent(
        new JsonObject()
            .put("type", "ERROR")
            .put("data", new JsonObject().put("errorCode", "LLM_ERROR")));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "ERROR")
            .put("data", new JsonObject().put("errorCode", "LLM_ERROR")));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "ERROR")
            .put("data", new JsonObject().put("errorCode", "TIMEOUT")));

    JsonObject counters = aggregator.snapshot().getJsonObject("counters");
    assertEquals(2L, counters.getLong("ERROR_COUNT.LLM_ERROR"));
    assertEquals(1L, counters.getLong("ERROR_COUNT.TIMEOUT"));
  }

  @Test
  void turnDurationAndToolCount() {
    long now = System.currentTimeMillis();

    aggregator.processEvent(
        new JsonObject().put("type", "TURN_STARTED").put("timestamp", now).put("spanId", "turn-1"));

    aggregator.processEvent(
        new JsonObject()
            .put("type", "TURN_FINISHED")
            .put("timestamp", now + 2000)
            .put("spanId", "turn-1")
            .put("data", new JsonObject().put("toolCount", 3)));

    JsonObject histograms = aggregator.snapshot().getJsonObject("histograms");
    assertEquals(2000.0, histograms.getJsonObject("TURN_DURATION_MS").getDouble("avg"));
    assertEquals(3.0, histograms.getJsonObject("TOOLS_PER_TURN").getDouble("avg"));
  }

  @Test
  void unknownTypeIgnored() {
    aggregator.processEvent(new JsonObject().put("type", "TOTALLY_UNKNOWN_EVENT"));

    JsonObject snapshot = aggregator.snapshot();
    assertTrue(snapshot.getJsonObject("counters").isEmpty());
    assertTrue(snapshot.getJsonObject("histograms").isEmpty());
  }

  @Test
  void nullTypeIgnored() {
    aggregator.processEvent(new JsonObject());

    JsonObject snapshot = aggregator.snapshot();
    assertTrue(snapshot.getJsonObject("counters").isEmpty());
  }
}
