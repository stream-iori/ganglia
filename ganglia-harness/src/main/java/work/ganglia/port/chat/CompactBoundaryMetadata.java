package work.ganglia.port.chat;

/**
 * Metadata for a compact boundary turn, marking a compression point in the conversation history.
 *
 * <p>Compact boundaries enable:
 *
 * <ul>
 *   <li>History navigation - finding messages before/after compression
 *   <li>Compression auditing - tracking when and why compressions occurred
 *   <li>Chain linking - connecting multiple compression events
 * </ul>
 *
 * @param trigger the trigger type: "auto" for automatic threshold-based, "manual" for
 *     user-initiated
 * @param preTokens token count before compression
 * @param postTokens token count after compression
 * @param messagesSummarized number of messages that were summarized
 * @param timestamp when the compression occurred (epoch millis)
 * @param previousBoundaryId ID of the previous boundary for chain navigation, or null if this is
 *     the first
 */
public record CompactBoundaryMetadata(
    String trigger,
    int preTokens,
    int postTokens,
    int messagesSummarized,
    long timestamp,
    String previousBoundaryId) {

  /** Creates metadata for an automatic compression. */
  public static CompactBoundaryMetadata auto(
      int preTokens, int postTokens, int messagesSummarized, String previousBoundaryId) {
    return new CompactBoundaryMetadata(
        "auto",
        preTokens,
        postTokens,
        messagesSummarized,
        System.currentTimeMillis(),
        previousBoundaryId);
  }

  /** Creates metadata for a manual compression. */
  public static CompactBoundaryMetadata manual(
      int preTokens, int postTokens, int messagesSummarized, String previousBoundaryId) {
    return new CompactBoundaryMetadata(
        "manual",
        preTokens,
        postTokens,
        messagesSummarized,
        System.currentTimeMillis(),
        previousBoundaryId);
  }

  /** Creates metadata for a forced compression (aggressive mode due to force limit exceeded). */
  public static CompactBoundaryMetadata forced(
      int preTokens, int postTokens, int messagesSummarized, String previousBoundaryId) {
    return new CompactBoundaryMetadata(
        "forced",
        preTokens,
        postTokens,
        messagesSummarized,
        System.currentTimeMillis(),
        previousBoundaryId);
  }

  /** Returns the compression ratio (postTokens / preTokens). */
  public double compressionRatio() {
    return preTokens > 0 ? (double) postTokens / preTokens : 0.0;
  }

  /** Returns the number of tokens saved by compression. */
  public int tokensSaved() {
    return Math.max(0, preTokens - postTokens);
  }
}
