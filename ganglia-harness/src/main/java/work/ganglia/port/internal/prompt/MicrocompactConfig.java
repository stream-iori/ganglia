package work.ganglia.port.internal.prompt;

/**
 * Configuration for time-based microcompact operations.
 *
 * <p>Time-based microcompact clears old tool results when the gap since the last assistant message
 * exceeds a threshold, indicating the LLM provider's prompt cache has likely expired.
 *
 * @param enabled whether time-based microcompact is enabled
 * @param gapThresholdMinutes minimum gap in minutes since last assistant message to trigger
 *     microcompact (default 60, matching typical cache TTL)
 * @param keepRecent number of most recent tool results to preserve (default 5)
 */
public record MicrocompactConfig(boolean enabled, int gapThresholdMinutes, int keepRecent) {

  /** Returns a config with the default values. */
  public static MicrocompactConfig defaults() {
    return new MicrocompactConfig(true, 60, 5);
  }

  /** Returns a disabled config. */
  public static MicrocompactConfig disabled() {
    return new MicrocompactConfig(false, 60, 5);
  }
}
