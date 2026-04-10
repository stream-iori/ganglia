package work.ganglia.port.internal.state;

import java.util.Map;

import work.ganglia.port.internal.prompt.ContextBudget;

/**
 * Semantic interface for publishing context management events.
 *
 * <p>This interface provides type-safe methods for publishing context-related observations,
 * abstracting away the details of event construction and dispatching.
 *
 * <p>Benefits over direct ObservationDispatcher usage:
 *
 * <ul>
 *   <li>Type-safe method signatures
 *   <li>IDE auto-completion for available events
 *   <li>Centralized event format management
 *   <li>Easier mocking in unit tests
 * </ul>
 */
public interface ContextEventPublisher {

  // ── Compression Events ─────────────────────────────────────────────────────

  /**
   * Publishes a compression started event.
   *
   * @param sessionId the session ID
   * @param beforeTokens the token count before compression
   */
  void publishCompressionStarted(String sessionId, int beforeTokens);

  /**
   * Publishes a compression finished event.
   *
   * @param sessionId the session ID
   * @param beforeTokens the token count before compression
   * @param afterTokens the token count after compression
   * @param success whether compression succeeded
   */
  void publishCompressionFinished(
      String sessionId, int beforeTokens, int afterTokens, boolean success);

  // ── Pressure Events ────────────────────────────────────────────────────────

  /**
   * Publishes a context pressure changed event.
   *
   * @param sessionId the session ID
   * @param level the pressure level (NORMAL, WARNING, CRITICAL, BLOCKING)
   * @param currentTokens the current token count
   * @param limit the context limit
   */
  void publishPressureChanged(String sessionId, String level, int currentTokens, int limit);

  // ── Budget Events ───────────────────────────────────────────────────────────

  /**
   * Publishes a context budget allocated event.
   *
   * @param sessionId the session ID
   * @param budget the allocated context budget
   */
  void publishBudgetAllocated(String sessionId, ContextBudget budget);

  // ── Analysis Events ─────────────────────────────────────────────────────────

  /**
   * Publishes a context analysis event.
   *
   * @param sessionId the session ID
   * @param analysis the context analysis data
   */
  void publishContextAnalysis(String sessionId, Map<String, Object> analysis);

  // ── Cache Events ────────────────────────────────────────────────────────────

  /**
   * Publishes a prompt cache statistics event.
   *
   * @param sessionId the session ID
   * @param stats the cache statistics
   */
  void publishPromptCacheStats(String sessionId, Map<String, Object> stats);
}
