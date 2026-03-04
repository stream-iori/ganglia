package work.ganglia.core.state;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;

public interface StateEngine {

    /**
     * Loads a session from disk/storage.
     */
    Future<SessionContext> loadSession(String sessionId);

    /**
     * Saves the current state of a session.
     * Must be atomic to ensure crash recovery.
     */
    Future<Void> saveSession(SessionContext context);

    /**
     * Creates a new empty session.
     */
    SessionContext createSession();
}
