package work.ganglia.port.internal.memory.model;

import java.time.Instant;

/**
 * Lightweight search result for a session query.
 *
 * @param sessionId The session identifier.
 * @param goal The session's goal.
 * @param matchSnippet A snippet from the session that matched the query.
 * @param startTime When the session started.
 */
public record SessionSummary(
    String sessionId, String goal, String matchSnippet, Instant startTime) {}
