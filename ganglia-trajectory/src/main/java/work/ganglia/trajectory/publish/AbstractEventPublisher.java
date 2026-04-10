package work.ganglia.trajectory.publish;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

import work.ganglia.kernel.loop.AgentLoopObserver;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.trajectory.event.EventType;
import work.ganglia.trajectory.event.TrajectoryEvent;

/** Base class for agent-specific event publishers that bridge ObservationDispatcher to UI. */
public abstract class AbstractEventPublisher implements AgentLoopObserver {

  private static final Logger logger = LoggerFactory.getLogger(AbstractEventPublisher.class);

  protected final Vertx vertx;

  protected AbstractEventPublisher(Vertx vertx) {
    this.vertx = vertx;
  }

  /** The EventBus address for WebSocket broadcast events. */
  protected abstract String eventsAddress();

  /** The EventBus address for the session cache (send, not publish). */
  protected abstract String cacheAddress();

  @Override
  public void onObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    onObservation(sessionId, type, content, data, null, null);
  }

  @Override
  public abstract void onObservation(
      String sessionId,
      ObservationType type,
      String content,
      Map<String, Object> data,
      String spanId,
      String parentSpanId);

  @Override
  public void onUsageRecorded(String sessionId, TokenUsage usage) {
    // Default no-op; subclasses can override
  }

  /** Publish an event to the EventBus with caching. */
  protected void publish(String sessionId, EventType type, Object data) {
    publish(sessionId, type, data, true);
  }

  /** Publish an event to the EventBus, optionally caching. */
  protected void publish(String sessionId, EventType type, Object data, boolean shouldCache) {
    vertx.runOnContext(
        v -> {
          TrajectoryEvent event =
              new TrajectoryEvent(
                  UUID.randomUUID().toString(), System.currentTimeMillis(), type, data);
          JsonObject json = JsonObject.mapFrom(event);
          DeliveryOptions opts = new DeliveryOptions().addHeader("sessionId", sessionId);

          vertx.eventBus().publish(eventsAddress(), json, opts);

          if (shouldCache) {
            vertx.eventBus().send(cacheAddress(), json, opts);
          }
        });
  }

  // ── Data extraction utilities ──────────────────────────────────────

  protected static String extractString(Map<String, Object> data, String key, String defaultValue) {
    if (data == null || !data.containsKey(key)) return defaultValue;
    Object val = data.get(key);
    return val != null ? val.toString() : defaultValue;
  }

  protected static int extractInt(Map<String, Object> data, String key, int defaultValue) {
    if (data == null || !data.containsKey(key)) return defaultValue;
    Object val = data.get(key);
    if (val instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(val.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  protected static long extractLong(Map<String, Object> data, String key, long defaultValue) {
    if (data == null || !data.containsKey(key)) return defaultValue;
    Object val = data.get(key);
    if (val instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(val.toString());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  protected static boolean extractBoolean(
      Map<String, Object> data, String key, boolean defaultValue) {
    if (data == null || !data.containsKey(key)) return defaultValue;
    Object val = data.get(key);
    if (val instanceof Boolean b) return b;
    return Boolean.parseBoolean(val.toString());
  }
}
