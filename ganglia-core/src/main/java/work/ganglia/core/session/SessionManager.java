package work.ganglia.core.session;

import io.vertx.core.Future;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.SessionContext;
import work.ganglia.memory.ContextCompressor;

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

    /**
     * Adds a steering message (interruption/guidance) to the specified session's queue.
     */
    void addSteeringMessage(String sessionId, String message);

    /**
     * Polls and removes all currently queued steering messages for the specified session.
     */
    List<String> pollSteeringMessages(String sessionId);
}
