package me.stream.ganglia.core.session;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.state.LogManager;
import me.stream.ganglia.core.state.StateEngine;

import java.util.Collections;
import java.util.List;

public class DefaultSessionManager implements SessionManager {
    private final StateEngine stateEngine;
    private final LogManager logManager;

    public DefaultSessionManager(StateEngine stateEngine, LogManager logManager) {
        this.stateEngine = stateEngine;
        this.logManager = logManager;
    }

    @Override
    public Future<SessionContext> getSession(String sessionId) {
        return stateEngine.loadSession(sessionId)
                .recover(err -> Future.succeededFuture(createSession(sessionId)));
    }

    @Override
    public Future<Void> persist(SessionContext context) {
        return stateEngine.saveSession(context)
                .compose(v -> logManager != null ? logManager.appendLog(context) : Future.succeededFuture());
    }

    @Override
    public SessionContext createSession(String sessionId) {
        return new SessionContext(
            sessionId,
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            new ModelOptions(0.0, 2048, "gpt-4o"),
            ToDoList.empty()
        );
    }

    @Override
    public SessionContext startTurn(SessionContext context, Message userMessage) {
        return context.startTurn(userMessage);
    }

    @Override
    public SessionContext addStep(SessionContext context, Message step) {
        return context.addStep(step);
    }

    @Override
    public SessionContext completeTurn(SessionContext context, Message response) {
        return context.completeTurn(response);
    }

    @Override
    public Future<List<String>> listSessions() {
        return Future.succeededFuture(Collections.emptyList());
    }

    @Override
    public Future<Void> deleteSession(String sessionId) {
        return Future.succeededFuture();
    }
}
