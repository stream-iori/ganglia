package work.ganglia.infrastructure.internal.state;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.llm.LLMException;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.util.TokenCounter;

/**
 * Handles PTL (Prompt Too Long) retry logic for compression operations.
 *
 * <p>When a compression request fails due to prompt length exceeding model limits, this handler
 * drops a percentage of the oldest turns and retries, up to a maximum number of attempts.
 *
 * <p>This is aligned with Claude Code's compact.ts approach.
 */
public class PTLRetryHandler {
  private static final Logger logger = LoggerFactory.getLogger(PTLRetryHandler.class);

  /** Maximum number of PTL retries. */
  private static final int MAX_PTL_RETRIES = 3;

  /** Fraction of turns to drop on each retry (20%). */
  private static final double DROP_FRACTION = 0.2;

  private final ContextCompressor compressor;
  private final TokenCounter tokenCounter;

  public PTLRetryHandler(ContextCompressor compressor, TokenCounter tokenCounter) {
    this.compressor = compressor;
    this.tokenCounter = tokenCounter;
  }

  /**
   * Compresses a list of turns with PTL retry logic.
   *
   * @param turns the turns to compress
   * @param retryCount current retry count (start with 0)
   * @return a future containing the compressed summary
   */
  public Future<String> compressWithRetry(List<Turn> turns, int retryCount) {
    return compressor
        .compress(turns)
        .recover(
            error -> {
              if (isPromptTooLongError(error) && retryCount < MAX_PTL_RETRIES) {
                logger.warn(
                    "Compression failed with PTL (attempt {}/{}). Dropping oldest {}% turns.",
                    retryCount + 1, MAX_PTL_RETRIES, (int) (DROP_FRACTION * 100));

                int dropCount = Math.max(1, (int) (turns.size() * DROP_FRACTION));
                List<Turn> remaining = turns.subList(dropCount, turns.size());

                if (remaining.isEmpty()) {
                  logger.warn("All turns dropped, returning truncation marker");
                  return Future.succeededFuture("[All turns truncated due to prompt length limit]");
                }

                return compressWithRetry(remaining, retryCount + 1)
                    .map(summary -> "[Earlier " + dropCount + " turns truncated]\n\n" + summary);
              }

              logger.error(
                  "Compression failed after {} retries: {}", retryCount, error.getMessage());
              return Future.failedFuture(error);
            });
  }

  /**
   * Compresses a single chunk with PTL retry logic.
   *
   * @param chunk the chunk of turns to compress
   * @param retryCount current retry count
   * @return a future containing the compressed summary
   */
  public Future<String> compressChunkWithRetry(List<Turn> chunk, int retryCount) {
    return compressor
        .compress(chunk)
        .recover(
            error -> {
              if (isPromptTooLongError(error) && retryCount < MAX_PTL_RETRIES) {
                logger.warn(
                    "Chunk compression failed with PTL (attempt {}/{}). Dropping oldest 20% turns.",
                    retryCount + 1, MAX_PTL_RETRIES);

                int dropCount = Math.max(1, (int) (chunk.size() * DROP_FRACTION));
                List<Turn> remaining = chunk.subList(dropCount, chunk.size());

                if (remaining.isEmpty()) {
                  return Future.succeededFuture("[Chunk truncated due to prompt length limit]");
                }

                return compressChunkWithRetry(remaining, retryCount + 1);
              }

              logger.error("Chunk compression failed: {}", error.getMessage());
              // Fall back to simple truncation
              return Future.succeededFuture(buildTruncatedSummary(chunk));
            });
  }

  /**
   * Checks if an error is a "prompt too long" error.
   *
   * @param error the error to check
   * @return true if this is a PTL error
   */
  public static boolean isPromptTooLongError(Throwable error) {
    if (error instanceof LLMException llmErr) {
      return llmErr.errorCode().isPresent()
          && (llmErr.errorCode().get().contains("prompt")
              || llmErr.httpStatusCode().map(status -> status == 400).orElse(false));
    }
    // Also check cause chain
    if (error.getCause() != null) {
      return isPromptTooLongError(error.getCause());
    }
    return false;
  }

  /** Builds a truncated summary when compression fails. */
  private String buildTruncatedSummary(List<Turn> turns) {
    StringBuilder sb = new StringBuilder("[Truncated chunk]\n");
    for (Turn t : turns) {
      String turnStr = t.toString();
      sb.append("- ").append(turnStr.substring(0, Math.min(turnStr.length(), 200))).append("...\n");
    }
    return sb.toString();
  }

  /** Returns the maximum number of PTL retries. */
  public static int getMaxPtlRetries() {
    return MAX_PTL_RETRIES;
  }

  /** Returns the drop fraction for PTL retries. */
  public static double getDropFraction() {
    return DROP_FRACTION;
  }
}
