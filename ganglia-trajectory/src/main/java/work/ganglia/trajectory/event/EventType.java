package work.ganglia.trajectory.event;

/**
 * Marker interface for event types dispatched to UI clients. Not sealed — domain modules (coding,
 * trading) provide their own implementations.
 */
public interface EventType {
  /** Returns the wire name (e.g. "THOUGHT", "TOOL_START"). */
  String name();
}
