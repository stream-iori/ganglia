package work.ganglia.port.internal.prompt;

/**
 * Token-budget constants for LLM-based context compression calls. Centralises the magic numbers
 * previously scattered across {@code DefaultContextCompressor} and {@code
 * LLMObservationCompressor}.
 *
 * <p>All thresholds are expressed as fractions of the utility model's context limit so they scale
 * automatically when the utility model changes.
 *
 * @param chunkingThreshold fraction of utilityContextLimit above which turns are split into chunks
 *     before compression (default 0.8)
 * @param chunkSize fraction of utilityContextLimit used as the per-chunk token budget (default 0.6)
 * @param reflectMaxTokens max generation tokens for the reflect() call
 * @param compressMaxTokens max generation tokens for compress / compressText / extractKeyFacts
 *     calls
 * @param summaryTokenLimit soft token limit written into the compression prompt ("keep under N
 *     tokens")
 * @param observationCompressMaxTokens max generation tokens for observation (tool output)
 *     compression
 */
public record CompressionBudget(
    double chunkingThreshold,
    double chunkSize,
    int reflectMaxTokens,
    int compressMaxTokens,
    int summaryTokenLimit,
    int observationCompressMaxTokens) {

  /** Returns a budget with the default values used throughout Ganglia. */
  public static CompressionBudget defaults() {
    return new CompressionBudget(0.8, 0.6, 1024, 2048, 1500, 1024);
  }
}
