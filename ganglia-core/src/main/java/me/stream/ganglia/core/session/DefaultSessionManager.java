package me.stream.ganglia.core.session;

import io.vertx.core.Future;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.core.state.LogManager;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.tools.model.ToDoList;

import java.util.Collections;
import java.util.List;

public class DefaultSessionManager implements SessionManager {
    private final StateEngine stateEngine;
    private final LogManager logManager;
    private final ConfigManager configManager;

    public DefaultSessionManager(StateEngine stateEngine, LogManager logManager, ConfigManager configManager) {
        this.stateEngine = stateEngine;
        this.logManager = logManager;
        this.configManager = configManager;
    }

    @Override
    public Future<SessionContext> getSession(String sessionId) {
        return stateEngine.loadSession(sessionId)
                .map(this::ensureModelOptions)
                .recover(err -> Future.succeededFuture(createSession(sessionId)));
    }

    private SessionContext ensureModelOptions(SessionContext context) {
        if (context.modelOptions() == null) {
            ModelOptions options = new ModelOptions(
                    configManager.getTemperature(),
                    configManager.getMaxTokens(),
                    configManager.getModel()
            );
            return context.withModelOptions(options);
        }
        return context;
    }

    @Override
    public Future<Void> persist(SessionContext context) {
        return stateEngine.saveSession(context)
                .compose(v -> logManager != null ? logManager.appendLog(context) : Future.succeededFuture());
    }

    @Override
    public SessionContext createSession(String sessionId) {
        ModelOptions options = new ModelOptions(
                configManager.getTemperature(),
                configManager.getMaxTokens(),
                configManager.getModel()
        );
        return new SessionContext(
            sessionId,
            Collections.emptyList(),
            null,
            Collections.emptyMap(),
            Collections.emptyList(),
            options,
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
