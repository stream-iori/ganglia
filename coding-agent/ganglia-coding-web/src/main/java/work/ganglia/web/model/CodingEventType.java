package work.ganglia.web.model;

import work.ganglia.trajectory.event.EventType;

/** Coding-agent-specific event types that extend the base trajectory events. */
public enum CodingEventType implements EventType {
  TOOL_OUTPUT_STREAM,
  ASK_USER,
  FILE_CONTENT,
  FILE_TREE,
  INIT_CONFIG,
  PLAN_UPDATED
}
