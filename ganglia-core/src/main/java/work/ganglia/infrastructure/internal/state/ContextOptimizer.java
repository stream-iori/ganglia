package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;

/**
 * Strategy for optimizing the session context (e.g., via compression) to fit within token limits.
 */
public interface ContextOptimizer {
    /**
     * Checks if the context needs optimization and performs it if necessary.
     * @param context The current session context.
     * @return A Future containing the optimized (or original) SessionContext.
     */
    Future<SessionContext> optimizeIfNeeded(SessionContext context);
}