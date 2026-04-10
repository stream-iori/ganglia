package work.ganglia.trajectory.event;

/** Unified event envelope dispatched to UI clients via EventBus. */
public record TrajectoryEvent(String eventId, long timestamp, EventType type, Object data) {}
