package work.ganglia.port.internal.memory.model;

public enum MemoryCategory {
  // Event-type categories (original)
  DECISION,
  BUGFIX,
  REFACTOR,
  FEATURE,
  ENVIRONMENT,
  OBSERVATION, // Specifically for compressed tool outputs

  // Knowledge-type categories (new)
  USER_PREFERENCE, // User communication style, work habits, technical background
  CONVENTION, // Project conventions, coding standards, team agreements
  LESSON_LEARNED, // Insights from debugging, architecture decisions, gotchas
  SKILL_PATTERN, // Reusable operation patterns that could become Skills

  UNKNOWN
}
