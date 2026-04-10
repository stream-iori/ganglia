package work.ganglia.port.internal.prompt;

/**
 * Unified token-budget allocation derived from the model's context window. Replaces all hardcoded
 * token constants previously scattered across DefaultPromptEngine, DefaultContextOptimizer and
 * GangliaKernel.
 *
 * @param contextLimit original context window size
 * @param maxGenerationTokens tokens reserved for model generation
 * @param systemPrompt system prompt fragments upper bound
 * @param history getPrunedHistory budget
 * @param currentTurnBudget current turn upper bound within the history budget
 * @param toolOutputPerMessage single tool output upper bound
 * @param toolOutputAggregate aggregate budget for all tool outputs in a turn
 * @param observationFallback ObservationCompressionHook degraded-truncation upper bound
 * @param compressionTarget DefaultContextOptimizer post-compression retention target
 */
public record ContextBudget(
    int contextLimit,
    int maxGenerationTokens,
    int systemPrompt,
    int history,
    int currentTurnBudget,
    int toolOutputPerMessage,
    int toolOutputAggregate,
    int observationFallback,
    int compressionTarget) {

  /**
   * Derives a complete budget from the raw context-window size and generation reserve.
   *
   * <p>Allocation fractions (of {@code available = contextLimit - maxGenerationTokens}):
   *
   * <ul>
   *   <li>systemPrompt: 5 % (clamped 1 500 – 8 000)
   *   <li>history: 80 % (clamped 2 000 – 200 000)
   *   <li>currentTurnBudget: 70 % of history (clamped 2 000 – 150 000)
   *   <li>toolOutputPerMessage: 4 % (clamped 2 000 – 16 000)
   *   <li>toolOutputAggregate: 20 % (clamped 4 000 – 80 000)
   *   <li>observationFallback: 2 % (clamped 1 000 – 4 000)
   *   <li>compressionTarget: 50 % (clamped 2 000 – 250 000)
   * </ul>
   */
  public static ContextBudget from(int contextLimit, int maxGenerationTokens) {
    int available = contextLimit - maxGenerationTokens;
    int historyBudget = clamp((int) (available * 0.80), 2000, 200000);
    return new ContextBudget(
        contextLimit,
        maxGenerationTokens,
        clamp((int) (available * 0.05), 1500, 8000),
        historyBudget,
        clamp((int) (historyBudget * 0.70), 2000, 150000),
        clamp((int) (available * 0.04), 2000, 16000),
        clamp((int) (available * 0.20), 4000, 80000),
        clamp((int) (available * 0.02), 1000, 4000),
        clamp((int) (available * 0.50), 2000, 250000));
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
