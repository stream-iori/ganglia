package work.ganglia.port.internal.memory;

import work.ganglia.port.chat.Turn;

/**
 * Represents an event in the memory system lifecycle.
 *
 * @param type The type of event.
 * @param sessionId The ID of the session.
 * @param goal The session's primary goal.
 * @param turn The Turn object containing interaction history (can be null for some events).
 */
public record MemoryEvent(EventType type, String sessionId, String goal, Turn turn) {
  public enum EventType {
    TURN_COMPLETED,
    TASK_DONE,
    SESSION_CLOSED
  }
}
