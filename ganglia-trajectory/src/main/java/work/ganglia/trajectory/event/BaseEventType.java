package work.ganglia.trajectory.event;

/** Common event types shared by all agent UIs. */
public enum BaseEventType implements EventType {
  THOUGHT,
  TOOL_START,
  TOOL_RESULT,
  TOKEN,
  AGENT_MESSAGE,
  USER_MESSAGE,
  SYSTEM_ERROR,
  SESSION_STARTED,
  SESSION_ENDED
}
