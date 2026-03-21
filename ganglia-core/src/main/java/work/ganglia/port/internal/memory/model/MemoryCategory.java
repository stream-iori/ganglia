package work.ganglia.port.internal.memory.model;

public enum MemoryCategory {
  DECISION,
  BUGFIX,
  REFACTOR,
  FEATURE,
  ENVIRONMENT,
  OBSERVATION, // Specifically for compressed tool outputs
  UNKNOWN
}
