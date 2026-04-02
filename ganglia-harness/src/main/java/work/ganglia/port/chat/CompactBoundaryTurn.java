package work.ganglia.port.chat;

/**
 * A turn that marks a compression boundary in the conversation history.
 *
 * <p>Compact boundary turns are inserted at the start of compressed history to mark where
 * compression occurred. They contain metadata about the compression for auditing and navigation.
 */
public record CompactBoundaryTurn(String id, CompactBoundaryMetadata metadata, Message message) {

  /** ID prefix for all compact boundary turns. */
  public static final String ID_PREFIX = "compact-boundary-";

  /**
   * Creates a compact boundary turn with the given metadata.
   *
   * @param metadata the compression metadata
   * @return a new CompactBoundaryTurn
   */
  public static CompactBoundaryTurn create(CompactBoundaryMetadata metadata) {
    String id = ID_PREFIX + metadata.timestamp();
    String content = buildBoundaryContent(metadata);
    Message msg = Message.system(content);
    return new CompactBoundaryTurn(id, metadata, msg);
  }

  private static String buildBoundaryContent(CompactBoundaryMetadata m) {
    return String.format(
        "[COMPACT BOUNDARY] %s compression - %d → %d tokens (%d messages summarized, %d saved)",
        m.trigger(), m.preTokens(), m.postTokens(), m.messagesSummarized(), m.tokensSaved());
  }

  /**
   * Converts this boundary turn to a regular Turn for storage in session history.
   *
   * @return a Turn representing this boundary
   */
  public Turn toTurn() {
    return Turn.newTurn(id, message);
  }

  /**
   * Checks if a turn ID represents a compact boundary.
   *
   * @param turnId the turn ID to check
   * @return true if the ID represents a compact boundary
   */
  public static boolean isCompactBoundaryId(String turnId) {
    return turnId != null && turnId.startsWith(ID_PREFIX);
  }
}
