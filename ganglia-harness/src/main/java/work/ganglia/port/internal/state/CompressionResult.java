package work.ganglia.port.internal.state;

import work.ganglia.port.chat.SessionContext;

/**
 * Result of a compression operation.
 *
 * @param context the compressed session context
 * @param summary the generated summary text (may be null for truncation)
 * @param tokensSaved tokens saved by compression
 * @param turnsSummarized number of turns that were summarized
 * @param strategyName name of the strategy that performed compression
 */
public record CompressionResult(
    SessionContext context,
    String summary,
    int tokensSaved,
    int turnsSummarized,
    String strategyName) {

  /** Creates a result with minimal information. */
  public static CompressionResult of(
      SessionContext context, int tokensSaved, int turnsSummarized, String strategyName) {
    return new CompressionResult(context, null, tokensSaved, turnsSummarized, strategyName);
  }
}
