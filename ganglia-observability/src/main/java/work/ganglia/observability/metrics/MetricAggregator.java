package work.ganglia.observability.metrics;

import java.util.DoubleSummaryStatistics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.util.Constants;

/**
 * In-memory metrics aggregator that subscribes to observation events and maintains counters and
 * histograms. Thread-safe for concurrent reads/writes.
 */
public class MetricAggregator {
  private static final Logger logger = LoggerFactory.getLogger(MetricAggregator.class);
  private static final int HISTOGRAM_MAX_SIZE = 1000;

  private final ConcurrentHashMap<String, LongAdder> counters = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Double>> histograms =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> pendingTimestamps = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> ttftTracked = new ConcurrentHashMap<>();

  private final Vertx vertx;

  public MetricAggregator(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Subscribe to the observation EventBus and start aggregating. */
  public void start() {
    vertx
        .eventBus()
        .<JsonObject>consumer(
            Constants.ADDRESS_OBSERVATIONS_ALL,
            msg -> {
              try {
                processEvent(msg.body());
              } catch (Exception e) {
                logger.debug("Failed to process observation event for metrics", e);
              }
            });
  }

  void processEvent(JsonObject event) {
    String typeStr = event.getString("type");
    if (typeStr == null) return;

    ObservationType type;
    try {
      type = ObservationType.valueOf(typeStr);
    } catch (IllegalArgumentException e) {
      return;
    }

    JsonObject data = event.getJsonObject("data");
    long timestamp = event.getLong("timestamp", System.currentTimeMillis());
    String spanId = event.getString("spanId");

    switch (type) {
      case MODEL_CALL_STARTED -> handleModelCallStarted(spanId, timestamp);
      case MODEL_CALL_FINISHED -> handleModelCallFinished(spanId, timestamp, data);
      case TOKEN_RECEIVED -> handleTokenReceived(spanId, timestamp);
      case TOKEN_USAGE_RECORDED -> handleTokenUsage(data);
      case PROMPT_CACHE_STATS -> handlePromptCacheStats(data);
      case TOOL_STARTED -> handleToolStarted(spanId, timestamp);
      case TOOL_FINISHED -> handleToolFinished(spanId, timestamp, data);
      case MCP_CALL_STARTED -> handleMcpCallStarted(spanId, timestamp);
      case MCP_CALL_FINISHED -> handleMcpCallFinished(spanId, timestamp);
      case TURN_STARTED -> handleTurnStarted(spanId, timestamp);
      case TURN_FINISHED -> handleTurnFinished(spanId, timestamp, data);
      case SESSION_STARTED -> handleSessionStarted(spanId, timestamp);
      case SESSION_ENDED -> handleSessionEnded(spanId, timestamp);
      case CONTEXT_COMPRESSED -> incrementCounter(MetricName.CONTEXT_COMPRESSION_COUNT);
      case ERROR -> incrementCounter(errorKey(data));
      case SESSION_ABORTED -> incrementCounter(MetricName.SESSION_ABORT_COUNT);
      default -> {}
    }
  }

  /** Return a JSON snapshot of all current metrics. */
  public JsonObject snapshot() {
    JsonObject result = new JsonObject();

    JsonObject counterSnapshot = new JsonObject();
    counters.forEach((key, adder) -> counterSnapshot.put(key, adder.sum()));
    result.put("counters", counterSnapshot);

    JsonObject histogramSnapshot = new JsonObject();
    histograms.forEach(
        (key, deque) -> {
          DoubleSummaryStatistics stats =
              deque.stream().mapToDouble(Double::doubleValue).summaryStatistics();
          if (stats.getCount() > 0) {
            histogramSnapshot.put(
                key,
                new JsonObject()
                    .put("count", stats.getCount())
                    .put("min", stats.getMin())
                    .put("max", stats.getMax())
                    .put("avg", Math.round(stats.getAverage() * 100.0) / 100.0));
          }
        });
    result.put("histograms", histogramSnapshot);

    return result;
  }

  // --- Model call metrics ---

  private void handleModelCallStarted(String spanId, long timestamp) {
    if (spanId != null) {
      pendingTimestamps.put("model:" + spanId, timestamp);
      ttftTracked.remove("ttft:" + spanId);
    }
  }

  private void handleModelCallFinished(String spanId, long timestamp, JsonObject data) {
    if (spanId == null) return;
    Long start = pendingTimestamps.remove("model:" + spanId);
    if (start != null) {
      long latency = timestamp - start;
      String model = data != null ? data.getString("model", "unknown") : "unknown";
      recordHistogram(MetricName.MODEL_CALL_LATENCY_MS + "." + model, latency);
      recordHistogram(MetricName.MODEL_CALL_LATENCY_MS.name(), latency);
    }
  }

  private void handleTokenReceived(String spanId, long timestamp) {
    if (spanId == null) return;
    String ttftKey = "ttft:" + spanId;
    if (ttftTracked.putIfAbsent(ttftKey, Boolean.TRUE) == null) {
      Long start = pendingTimestamps.get("model:" + spanId);
      if (start != null) {
        recordHistogram(MetricName.MODEL_CALL_TTFT_MS.name(), timestamp - start);
      }
    }
  }

  private void handleTokenUsage(JsonObject data) {
    if (data == null) return;
    int prompt = data.getInteger("promptTokens", 0);
    int completion = data.getInteger("completionTokens", 0);
    addToCounter(MetricName.TOKEN_PROMPT_TOTAL, prompt);
    addToCounter(MetricName.TOKEN_COMPLETION_TOTAL, completion);
  }

  private void handlePromptCacheStats(JsonObject data) {
    if (data == null) return;
    int hits = data.getInteger("cacheHits", 0);
    int misses = data.getInteger("cacheMisses", 0);
    addToCounter(MetricName.PROMPT_CACHE_HIT_RATE.name() + ".hits", hits);
    addToCounter(MetricName.PROMPT_CACHE_HIT_RATE.name() + ".misses", misses);
  }

  // --- Tool metrics ---

  private void handleToolStarted(String spanId, long timestamp) {
    if (spanId != null) {
      pendingTimestamps.put("tool:" + spanId, timestamp);
    }
  }

  private void handleToolFinished(String spanId, long timestamp, JsonObject data) {
    if (spanId != null) {
      Long start = pendingTimestamps.remove("tool:" + spanId);
      if (start != null) {
        long duration = timestamp - start;
        String toolName = data != null ? data.getString("toolName", "unknown") : "unknown";
        recordHistogram(MetricName.TOOL_EXECUTION_DURATION_MS + "." + toolName, duration);
        recordHistogram(MetricName.TOOL_EXECUTION_DURATION_MS.name(), duration);
      }
    }
    boolean isError = data != null && data.getBoolean("isError", false);
    incrementCounter(isError ? MetricName.TOOL_FAILURE_COUNT : MetricName.TOOL_SUCCESS_COUNT);
  }

  private void handleMcpCallStarted(String spanId, long timestamp) {
    if (spanId != null) {
      pendingTimestamps.put("mcp:" + spanId, timestamp);
    }
  }

  private void handleMcpCallFinished(String spanId, long timestamp) {
    if (spanId != null) {
      Long start = pendingTimestamps.remove("mcp:" + spanId);
      if (start != null) {
        recordHistogram(MetricName.MCP_CALL_DURATION_MS.name(), timestamp - start);
      }
    }
  }

  // --- Turn / Session metrics ---

  private void handleTurnStarted(String spanId, long timestamp) {
    if (spanId != null) {
      pendingTimestamps.put("turn:" + spanId, timestamp);
    }
  }

  private void handleTurnFinished(String spanId, long timestamp, JsonObject data) {
    if (spanId != null) {
      Long start = pendingTimestamps.remove("turn:" + spanId);
      if (start != null) {
        recordHistogram(MetricName.TURN_DURATION_MS.name(), timestamp - start);
      }
    }
    if (data != null) {
      int toolCount = data.getInteger("toolCount", 0);
      if (toolCount > 0) {
        recordHistogram(MetricName.TOOLS_PER_TURN.name(), toolCount);
      }
    }
  }

  private void handleSessionStarted(String spanId, long timestamp) {
    String sessionId = spanId != null ? spanId : "default";
    pendingTimestamps.put("session:" + sessionId, timestamp);
  }

  private void handleSessionEnded(String spanId, long timestamp) {
    String sessionId = spanId != null ? spanId : "default";
    Long start = pendingTimestamps.remove("session:" + sessionId);
    if (start != null) {
      recordHistogram(MetricName.SESSION_DURATION_MS.name(), timestamp - start);
    }
  }

  // --- Helpers ---

  private String errorKey(JsonObject data) {
    String code = data != null ? data.getString("errorCode", "UNKNOWN") : "UNKNOWN";
    return MetricName.ERROR_COUNT + "." + code;
  }

  private void incrementCounter(MetricName name) {
    counters.computeIfAbsent(name.name(), k -> new LongAdder()).increment();
  }

  private void incrementCounter(String key) {
    counters.computeIfAbsent(key, k -> new LongAdder()).increment();
  }

  private void addToCounter(MetricName name, long value) {
    counters.computeIfAbsent(name.name(), k -> new LongAdder()).add(value);
  }

  private void addToCounter(String key, long value) {
    counters.computeIfAbsent(key, k -> new LongAdder()).add(value);
  }

  private void recordHistogram(String key, double value) {
    ConcurrentLinkedDeque<Double> deque =
        histograms.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    deque.addLast(value);
    while (deque.size() > HISTOGRAM_MAX_SIZE) {
      deque.pollFirst();
    }
  }
}
