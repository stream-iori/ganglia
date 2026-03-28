package work.ganglia.port.internal.state;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;

/**
 * Strategy for optimizing the session context (e.g., via compression) to fit within token limits.
 */
public interface ContextOptimizer {
  /**
   * Checks if the context needs optimization and performs it if necessary.
   *
   * @param context The current session context.
   * @return A Future containing the optimized (or original) SessionContext.
   */
  default Future<SessionContext> optimizeIfNeeded(SessionContext context) {
    return optimizeIfNeeded(context, null);
  }

  /**
   * Checks if the context needs optimization and performs it if necessary.
   *
   * @param context The current session context.
   * @param parentSpanId The ID of the parent span (e.g. Turn span).
   * @return A Future containing the optimized (or original) SessionContext.
   */
  Future<SessionContext> optimizeIfNeeded(SessionContext context, String parentSpanId);
}
