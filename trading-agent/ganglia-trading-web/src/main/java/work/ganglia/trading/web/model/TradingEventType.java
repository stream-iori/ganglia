package work.ganglia.trading.web.model;

import work.ganglia.trajectory.event.EventType;

/** Trading-agent-specific event types that extend the base trajectory events. */
public enum TradingEventType implements EventType {
  // Pipeline lifecycle
  PIPELINE_STARTED,
  PIPELINE_PHASE_CHANGED,
  PIPELINE_COMPLETED,
  PIPELINE_ERROR,

  // Debate events (mapped from MANAGER_CYCLE_*)
  DEBATE_CYCLE_STARTED,
  DEBATE_CYCLE_FINISHED,
  DEBATE_CONVERGED,
  DEBATE_STALLED,

  // Blackboard events (mapped from FACT_*)
  FACT_PUBLISHED,
  FACT_SUPERSEDED,
  FACT_ARCHIVED,

  // Config
  INIT_CONFIG,

  // Signal history
  SIGNAL_HISTORY_UPDATE
}
