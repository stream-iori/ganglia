package work.ganglia.port.internal.memory;

import work.ganglia.port.chat.Turn;

/**
 * Event data for background reflection and recording.
 *
 * @param sessionId The ID of the session.
 * @param goal The session's primary goal.
 * @param turn The Turn object containing interaction history.
 */
public record ReflectEvent(String sessionId, String goal, Turn turn) {}
