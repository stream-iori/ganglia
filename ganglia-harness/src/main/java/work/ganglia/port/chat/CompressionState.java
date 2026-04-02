package work.ganglia.port.chat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Immutable state related to context compression for a session.
 *
 * <p>This record replaces the previous Map-based metadata storage for compression-related fields,
 * providing type safety and clear semantics.
 *
 * @param runningSummary the running summary of previous interactions, updated after each
 *     compression
 * @param consecutiveFailures number of consecutive compression failures (for circuit breaker)
 * @param lastAssistantTimestamp timestamp of the last assistant message (for time-based
 *     microcompact)
 * @param recentlyReadFiles list of recently read files for post-compression restoration
 */
public record CompressionState(
    String runningSummary,
    int consecutiveFailures,
    Instant lastAssistantTimestamp,
    List<RecentlyReadFile> recentlyReadFiles) {

  /** Creates a default empty compression state. */
  public static CompressionState empty() {
    return new CompressionState(null, 0, null, Collections.emptyList());
  }

  /** Creates a new state with an updated running summary. */
  public CompressionState withRunningSummary(String summary) {
    return new CompressionState(
        summary, consecutiveFailures, lastAssistantTimestamp, recentlyReadFiles);
  }

  /** Creates a new state with incremented failure count. */
  public CompressionState withFailure() {
    return new CompressionState(
        runningSummary, consecutiveFailures + 1, lastAssistantTimestamp, recentlyReadFiles);
  }

  /** Creates a new state with reset failure count. */
  public CompressionState resetFailures() {
    return new CompressionState(runningSummary, 0, lastAssistantTimestamp, recentlyReadFiles);
  }

  /** Creates a new state with updated last assistant timestamp. */
  public CompressionState withLastAssistantTimestamp(Instant timestamp) {
    return new CompressionState(runningSummary, consecutiveFailures, timestamp, recentlyReadFiles);
  }

  /** Creates a new state with updated recently read files. */
  public CompressionState withRecentlyReadFiles(List<RecentlyReadFile> files) {
    return new CompressionState(
        runningSummary,
        consecutiveFailures,
        lastAssistantTimestamp,
        files != null ? files : Collections.emptyList());
  }

  /** Returns true if the running summary is valid and non-trivial. */
  public boolean hasValidRunningSummary() {
    return runningSummary != null && !runningSummary.isBlank() && runningSummary.length() > 50;
  }

  /** Returns true if there are consecutive failures. */
  public boolean hasFailures() {
    return consecutiveFailures > 0;
  }
}
