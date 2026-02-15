package me.stream.ganglia.core.model;

import java.util.Map;

/**
 * Represents a structured observation event from the agent loop.
 */
public record ObservationEvent(
    String sessionId,
    ObservationType type,
    String content,
    Map<String, Object> data
) {
    public static ObservationEvent of(String sessionId, ObservationType type, String content) {
        return new ObservationEvent(sessionId, type, content, null);
    }

    public static ObservationEvent of(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        return new ObservationEvent(sessionId, type, content, data);
    }
}
