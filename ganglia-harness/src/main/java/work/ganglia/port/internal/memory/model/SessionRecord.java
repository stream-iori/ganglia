package work.ganglia.port.internal.memory.model;

import java.time.Instant;

/**
 * Represents a completed session with its metadata and summary.
 *
 * @param sessionId Unique session identifier.
 * @param goal The session's primary goal or task description.
 * @param summary Summarized accomplishments of the session.
 * @param turnCount Number of conversation turns in the session.
 * @param toolCallCount Number of tool calls made during the session.
 * @param startTime When the session started.
 * @param endTime When the session ended.
 */
public record SessionRecord(
    String sessionId,
    String goal,
    String summary,
    int turnCount,
    int toolCallCount,
    Instant startTime,
    Instant endTime) {}
