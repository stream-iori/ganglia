package work.ganglia.port.internal.state;

/**
 * Immutable context passed through the optimization pipeline.
 *
 * <p>Contains all the thresholds and limits needed by optimization steps to make decisions.
 */
public record OptimizationContext(
    int currentTokens,
    int contextLimit,
    int threshold,
    int forceLimit,
    int hardLimit,
    int historyTokens,
    int systemOverheadTokens) {

  /** Returns the threshold as a token count. */
  public int thresholdTokens() {
    // threshold is stored as percentage (e.g., 80 for 80%)
    return (int) (contextLimit * threshold / 100.0);
  }

  /** Returns true if current tokens exceed the compression threshold. */
  public boolean exceedsThreshold() {
    return currentTokens > thresholdTokens();
  }

  /** Returns true if current tokens exceed the force limit. */
  public boolean exceedsForceLimit() {
    return currentTokens > forceLimit;
  }

  /** Returns true if current tokens exceed the hard limit. */
  public boolean exceedsHardLimit() {
    return currentTokens > hardLimit;
  }

  /** Creates a new context with updated token counts. */
  public OptimizationContext withTokens(int newHistoryTokens, int newTotalTokens) {
    return new OptimizationContext(
        newTotalTokens,
        contextLimit,
        threshold,
        forceLimit,
        hardLimit,
        newHistoryTokens,
        systemOverheadTokens);
  }
}
