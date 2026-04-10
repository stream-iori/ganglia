package work.ganglia.port.internal.prompt;

/**
 * Statistics about prompt cache usage for monitoring and debugging.
 *
 * @param cacheHit true if the stable prefix was reused from cache
 * @param stablePrefixTokens token count of the cached stable prefix
 * @param stableFragmentCount number of cacheable fragments in stable prefix
 * @param volatileFragmentCount number of non-cacheable fragments
 */
public record PromptCacheStats(
    boolean cacheHit, int stablePrefixTokens, int stableFragmentCount, int volatileFragmentCount) {

  /** Returns an empty stats indicating no caching occurred. */
  public static PromptCacheStats empty() {
    return new PromptCacheStats(false, 0, 0, 0);
  }

  /** Returns stats indicating a cache miss with computed values. */
  public static PromptCacheStats miss(int stableTokens, int stableCount, int volatileCount) {
    return new PromptCacheStats(false, stableTokens, stableCount, volatileCount);
  }

  /** Returns stats indicating a cache hit. */
  public static PromptCacheStats hit(int stableTokens, int stableCount, int volatileCount) {
    return new PromptCacheStats(true, stableTokens, stableCount, volatileCount);
  }
}
