package work.ganglia.port.external.tool;

import java.util.Map;

/** Represents a structured observation event from the agent loop. */
public record ObservationEvent(
    String sessionId,
    ObservationType type,
    String content,
    Map<String, Object> data,
    long timestamp,
    String spanId,
    String parentSpanId) {

  public ObservationEvent {
    if (data != null) {
      data = Map.copyOf(data);
    }
  }

  public static ObservationEvent of(String sessionId, ObservationType type, String content) {
    return new ObservationEvent(
        sessionId, type, content, null, System.currentTimeMillis(), null, null);
  }

  public static ObservationEvent of(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    return new ObservationEvent(
        sessionId, type, content, data, System.currentTimeMillis(), null, null);
  }

  public static ObservationEvent of(
      String sessionId,
      ObservationType type,
      String content,
      Map<String, Object> data,
      String spanId,
      String parentSpanId) {
    return new ObservationEvent(
        sessionId, type, content, data, System.currentTimeMillis(), spanId, parentSpanId);
  }
}
