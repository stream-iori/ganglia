package me.stream.ganglia.core.state;

import me.stream.ganglia.core.model.SessionContext;
import java.util.concurrent.CompletionStage;

public interface StateEngine {
    
    /**
     * Loads a session from disk/storage.
     */
    CompletionStage<SessionContext> loadSession(String sessionId);

    /**
     * Saves the current state of a session.
     * Must be atomic to ensure crash recovery.
     */
    CompletionStage<Void> saveSession(SessionContext context);
    
    /**
     * Creates a new empty session.
     */
    SessionContext createSession();
}
