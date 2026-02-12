package me.stream.ganglia.core.session;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.SessionContext;

import java.util.List;

/**
 * Manages the lifecycle and persistence of agent sessions.
 */
public interface SessionManager {
    /**
     * Retrieves a session by its ID, or creates a new one if it doesn't exist.
     */
    Future<SessionContext> getSession(String sessionId);

    /**
     * Persists the current state of a session and logs the interaction.
     */
    Future<Void> persist(SessionContext context);

    /**
     * Creates a new session context with default values.
     */
    SessionContext createSession(String sessionId);

    /**
     * Transitions the session to a new turn starting with a user message.
     */
    SessionContext startTurn(SessionContext context, Message userMessage);

    /**
     * Appends a step (thought/tool result) to the current turn.
     */
    SessionContext addStep(SessionContext context, Message step);

    /**
     * Finalizes the current turn with a response.
     */
    SessionContext completeTurn(SessionContext context, Message response);

    /**
     * Lists all available session IDs.
     */
    Future<List<String>> listSessions();

    /**
     * Deletes a session and its associated data.
     */
    Future<Void> deleteSession(String sessionId);
}
