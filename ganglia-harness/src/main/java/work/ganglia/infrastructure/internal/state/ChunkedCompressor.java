package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.Turn;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.prompt.CompressionBudget;
import work.ganglia.util.TokenCounter;

/**
 * Handles compression of large turn sets by splitting into chunks.
 *
 * <p>When the total tokens of turns to compress exceeds a threshold, this compressor splits the
 * turns into chunks, compresses each chunk separately, then merges the summaries.
 *
 * <p>This prevents the compression request itself from exceeding the utility model's context limit
 * and causing a PTL error.
 */
public class ChunkedCompressor {
  private static final Logger logger = LoggerFactory.getLogger(ChunkedCompressor.class);

  private final ContextCompressor compressor;
  private final TokenCounter tokenCounter;
  private final CompressionBudget budget;
  private final PTLRetryHandler retryHandler;

  public ChunkedCompressor(
      ContextCompressor compressor, TokenCounter tokenCounter, CompressionBudget budget) {
    this.compressor = compressor;
    this.tokenCounter = tokenCounter;
    this.budget = budget;
    this.retryHandler = new PTLRetryHandler(compressor, tokenCounter);
  }

  /**
   * Compresses turns, using chunking if the total tokens exceed the threshold.
   *
   * @param turns the turns to compress
   * @param utilityContextLimit the utility model's context limit
   * @return a future containing the compressed summary
   */
  public Future<String> compress(List<Turn> turns, int utilityContextLimit) {
    int totalTokens = estimateTokens(turns);
    int chunkingThreshold = (int) (utilityContextLimit * budget.chunkingThreshold());

    if (totalTokens <= chunkingThreshold) {
      logger.debug("Single compression: {} tokens (threshold: {})", totalTokens, chunkingThreshold);
      return retryHandler.compressWithRetry(turns, 0);
    }

    logger.info(
        "Chunked compression triggered: {} tokens > {} threshold, splitting into chunks",
        totalTokens,
        chunkingThreshold);

    return compressWithChunks(turns, utilityContextLimit);
  }

  /**
   * Compresses turns in chunks.
   *
   * @param turns the turns to compress
   * @param utilityContextLimit the utility model's context limit
   * @return a future containing the merged summary
   */
  private Future<String> compressWithChunks(List<Turn> turns, int utilityContextLimit) {
    int chunkSize = (int) (utilityContextLimit * budget.chunkSize());
    List<List<Turn>> chunks = splitByTokenBudget(turns, chunkSize);

    logger.info("Splitting {} turns into {} chunks for compression", turns.size(), chunks.size());

    // Compress each chunk
    List<Future<String>> chunkFutures =
        chunks.stream().map(chunk -> retryHandler.compressChunkWithRetry(chunk, 0)).toList();

    return Future.join(chunkFutures)
        .compose(
            v -> {
              // Merge all chunk summaries
              List<String> summaries = chunkFutures.stream().map(Future::result).toList();
              String merged = String.join("\n\n---\n\n", summaries);

              // If merged summary is still too large, do a final compression
              int mergedTokens = tokenCounter.count(merged);
              int finalThreshold = (int) (utilityContextLimit * budget.chunkingThreshold());

              if (mergedTokens > finalThreshold) {
                logger.info(
                    "Merged chunk summaries still large ({} tokens), doing final compression",
                    mergedTokens);
                return compressor.compressText(merged);
              }

              return Future.succeededFuture(merged);
            });
  }

  /**
   * Splits turns into chunks where each chunk stays within the token budget.
   *
   * @param turns the turns to split
   * @param chunkTokenLimit the maximum tokens per chunk
   * @return a list of chunks
   */
  private List<List<Turn>> splitByTokenBudget(List<Turn> turns, int chunkTokenLimit) {
    List<List<Turn>> chunks = new ArrayList<>();
    List<Turn> currentChunk = new ArrayList<>();
    int currentTokens = 0;

    for (Turn t : turns) {
      int turnTokens = t.flatten().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();

      // If adding this turn would exceed the limit and chunk is not empty, start a new chunk
      if (!currentChunk.isEmpty() && currentTokens + turnTokens > chunkTokenLimit) {
        chunks.add(currentChunk);
        currentChunk = new ArrayList<>();
        currentTokens = 0;
      }

      currentChunk.add(t);
      currentTokens += turnTokens;
    }

    // Add the last chunk if not empty
    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk);
    }

    return chunks;
  }

  /** Estimates the total token count for a list of turns. */
  private int estimateTokens(List<Turn> turns) {
    return turns.stream()
        .flatMap(t -> t.flatten().stream())
        .mapToInt(m -> m.countTokens(tokenCounter))
        .sum();
  }
}
