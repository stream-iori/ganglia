package work.ganglia.infrastructure.internal.prompt.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.kernel.hook.builtin.TokenAwareTruncator;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.PromptCacheStats;
import work.ganglia.util.TokenCounter;

/**
 * Composes fragments into a final prompt string, applying pruning based on priority. Supports
 * caching of stable (cacheable) fragments for prompt cache optimization.
 */
public class ContextComposer {
  private static final Logger logger = LoggerFactory.getLogger(ContextComposer.class);

  private final TokenCounter tokenCounter;
  private final TokenAwareTruncator truncator;

  // Cache for stable prefix (cacheable fragments)
  private String cachedStablePrefix = null;
  private int cachedStablePrefixTokens = 0;
  private List<ContextFragment> lastStableFragments = null;

  // Last cache stats for monitoring
  private PromptCacheStats lastCacheStats = null;

  public ContextComposer(TokenCounter tokenCounter) {
    this(tokenCounter, null);
  }

  public ContextComposer(TokenCounter tokenCounter, Integer maxTruncationTokens) {
    this.tokenCounter = tokenCounter;
    this.truncator =
        new TokenAwareTruncator(
            tokenCounter, maxTruncationTokens != null ? maxTruncationTokens : 16000);
  }

  /**
   * Composes fragments with stable/volatile separation for prompt cache optimization. Cacheable
   * fragments are placed first (stable prefix) and cached across calls. Non-cacheable fragments
   * (volatile) are placed after and recomputed each call.
   */
  public String compose(List<ContextFragment> fragments, int maxTokens) {
    // Split into stable (cacheable) and volatile (non-cacheable)
    List<ContextFragment> stable =
        fragments.stream()
            .filter(ContextFragment::cacheable)
            .sorted(Comparator.comparingInt(ContextFragment::priority))
            .toList();

    List<ContextFragment> volatile_ =
        fragments.stream()
            .filter(f -> !f.cacheable())
            .sorted(Comparator.comparingInt(ContextFragment::priority))
            .toList();

    // Check if stable prefix needs recalculation
    boolean cacheHit = cachedStablePrefix != null && fragmentsMatch(lastStableFragments, stable);
    if (!cacheHit) {
      cachedStablePrefix = renderFragments(stable);
      cachedStablePrefixTokens = tokenCounter.count(cachedStablePrefix);
      lastStableFragments = new ArrayList<>(stable);
      logger.debug(
          "Prompt cache miss: recomputed stable prefix ({} fragments, {} tokens)",
          stable.size(),
          cachedStablePrefixTokens);
    } else {
      logger.debug(
          "Prompt cache hit: reusing stable prefix ({} fragments, {} tokens)",
          stable.size(),
          cachedStablePrefixTokens);
    }

    // Record cache stats
    lastCacheStats =
        cacheHit
            ? PromptCacheStats.hit(cachedStablePrefixTokens, stable.size(), volatile_.size())
            : PromptCacheStats.miss(cachedStablePrefixTokens, stable.size(), volatile_.size());

    // Calculate volatile tokens
    Map<ContextFragment, Integer> volatileTokens = new HashMap<>();
    for (ContextFragment f : volatile_) {
      String withPrefix = "## " + f.name() + "\n" + f.content();
      volatileTokens.put(f, tokenCounter.count(withPrefix));
    }

    // Calculate available budget for volatile part
    int separatorOverhead =
        stable.isEmpty() || volatile_.isEmpty() ? 0 : 4; // "\n\n" between stable and volatile
    int availableForVolatile = maxTokens - cachedStablePrefixTokens - separatorOverhead;

    List<ContextFragment> toInclude = new ArrayList<>(volatile_);
    int volatileTokenSum = calculateTokensIncremental(toInclude, volatileTokens);

    // Prune volatile fragments if needed
    while (volatileTokenSum > availableForVolatile && hasPrunable(toInclude)) {
      volatileTokenSum = pruneOne(toInclude, volatileTokens, volatileTokenSum);
    }

    String volatilePart = renderFragments(toInclude);

    // Combine stable + volatile
    String result;
    if (cachedStablePrefix.isEmpty()) {
      result = volatilePart;
    } else if (volatilePart.isEmpty()) {
      result = cachedStablePrefix;
    } else {
      result = cachedStablePrefix + "\n\n" + volatilePart;
    }

    // Final safety check: hard truncate if still too large
    if (tokenCounter.count(result) > maxTokens) {
      return truncator.truncate(result, "system-prompt");
    }

    return result;
  }

  /** Returns the cached stable prefix for cache monitoring. */
  public String getStablePrefix() {
    return cachedStablePrefix;
  }

  /** Returns the token count of the cached stable prefix. */
  public int getStablePrefixTokens() {
    return cachedStablePrefixTokens;
  }

  /** Returns the cache statistics from the last compose call. */
  public PromptCacheStats getLastCacheStats() {
    return lastCacheStats != null ? lastCacheStats : PromptCacheStats.empty();
  }

  /** Clears the stable prefix cache (e.g., when tools are re-registered). */
  public void clearCache() {
    if (cachedStablePrefix != null) {
      logger.debug(
          "Prompt cache cleared (was {} tokens, {} fragments)",
          cachedStablePrefixTokens,
          lastStableFragments != null ? lastStableFragments.size() : 0);
    }
    cachedStablePrefix = null;
    cachedStablePrefixTokens = 0;
    lastStableFragments = null;
    lastCacheStats = null;
  }

  private boolean fragmentsMatch(List<ContextFragment> a, List<ContextFragment> b) {
    if (a == null || b == null) return a == b;
    if (a.size() != b.size()) return false;
    for (int i = 0; i < a.size(); i++) {
      if (!a.get(i).content().equals(b.get(i).content())) return false;
    }
    return true;
  }

  private String renderFragments(List<ContextFragment> fragments) {
    return fragments.stream()
        .map(f -> "## " + f.name() + "\n" + f.content())
        .collect(Collectors.joining("\n\n"));
  }

  /** Incremental token calculation - O(n) instead of O(n²) */
  private int calculateTokensIncremental(
      List<ContextFragment> fragments, Map<ContextFragment, Integer> fragmentTokens) {
    if (fragments.isEmpty()) {
      return 0;
    }
    // Sum all fragment tokens
    int sum = fragments.stream().mapToInt(f -> fragmentTokens.getOrDefault(f, 0)).sum();
    // Add separator overhead: "\n\n" between each pair = (n-1) * 2
    sum += Math.max(0, (fragments.size() - 1) * 2);
    return sum;
  }

  private boolean hasPrunable(List<ContextFragment> fragments) {
    return fragments.stream().anyMatch(f -> !f.isMandatory());
  }

  /**
   * Prune one fragment and return updated total token count. Uses incremental calculation for O(1)
   * update instead of O(n) recalculation.
   */
  private int pruneOne(
      List<ContextFragment> fragments,
      Map<ContextFragment, Integer> fragmentTokens,
      int currentTotalTokens) {
    // Find the non-mandatory fragment with the largest priority number
    ContextFragment victim =
        fragments.stream()
            .filter(f -> !f.isMandatory())
            .max(Comparator.comparingInt(ContextFragment::priority))
            .orElse(null);

    if (victim != null) {
      int victimTokens = fragmentTokens.getOrDefault(victim, 0);
      fragments.remove(victim);
      // Subtract victim tokens and separator overhead (2 chars)
      return currentTotalTokens - victimTokens - 2;
    }
    return currentTotalTokens;
  }
}
