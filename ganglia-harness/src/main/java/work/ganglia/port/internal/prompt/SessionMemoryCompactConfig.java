package work.ganglia.port.internal.prompt;

/**
 * Configuration for session memory based compression.
 *
 * <p>Session memory compact uses the pre-extracted running summary instead of making an LLM
 * compression call when conditions are met. This is faster and cheaper than full compression.
 *
 * @param minTokens minimum tokens in the turns to compress for session memory compact (default
 *     10_000)
 * @param minTextBlockMessages minimum number of messages with text content (default 5)
 * @param maxTokens maximum tokens in the turns to compress for session memory compact (default
 *     40_000)
 */
public record SessionMemoryCompactConfig(int minTokens, int minTextBlockMessages, int maxTokens) {

  /** Returns a config with the default values. */
  public static SessionMemoryCompactConfig defaults() {
    return new SessionMemoryCompactConfig(10_000, 5, 40_000);
  }
}
