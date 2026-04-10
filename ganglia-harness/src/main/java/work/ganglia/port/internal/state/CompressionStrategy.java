package work.ganglia.port.internal.state;

import io.vertx.core.Future;

/**
 * Strategy for compressing conversation history.
 *
 * <p>Different strategies can be used depending on context conditions:
 *
 * <ul>
 *   <li>{@link SessionMemoryCompressionStrategy} - uses pre-extracted running summary
 *   <li>{@link LLMCompressionStrategy} - uses LLM to generate summary
 *   <li>{@link TruncationCompressionStrategy} - simple truncation fallback
 * </ul>
 *
 * <p>Strategies can indicate whether they can handle a given request via {@link
 * #canHandle(CompressionRequest)}.
 */
public interface CompressionStrategy {

  /**
   * Returns the name of this strategy for logging and debugging.
   *
   * @return the strategy name
   */
  String name();

  /**
   * Determines whether this strategy can handle the given compression request.
   *
   * <p>This allows the strategy selector to choose the most appropriate strategy based on
   * conditions like token count, running summary availability, etc.
   *
   * @param request the compression request
   * @return true if this strategy can handle the request
   */
  boolean canHandle(CompressionRequest request);

  /**
   * Returns the priority of this strategy. Higher values are preferred.
   *
   * <p>When multiple strategies can handle a request, the one with highest priority is used.
   *
   * @return the priority value
   */
  default int priority() {
    return 0;
  }

  /**
   * Compresses the turns according to this strategy.
   *
   * @param request the compression request
   * @return a future containing the compression result
   */
  Future<CompressionResult> compress(CompressionRequest request);
}
