package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Future;
import work.ganglia.config.ConfigManager;
import work.ganglia.port.chat.*;
import work.ganglia.port.external.llm.*;
import work.ganglia.port.external.tool.*;
import work.ganglia.port.internal.state.*;;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.internal.state.LogManager;
import work.ganglia.port.internal.state.StateEngine;
import work.ganglia.infrastructure.internal.memory.ContextCompressor;
import work.ganglia.infrastructure.external.tool.model.ToDoList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultSessionManager implements SessionManager {
    private final StateEngine stateEngine;
    private final LogManager logManager;
    private final ConfigManager configManager;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> steeringQueues;

    public DefaultSessionManager(StateEngine stateEngine, LogManager logManager, ConfigManager configManager) {
        this.stateEngine = stateEngine;
        this.logManager = logManager;
        this.configManager = configManager;
        this.steeringQueues = new ConcurrentHashMap<>();
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
                configManager.getModel(),
                configManager.isStream()
            );
            return context.withModelOptions(options);
        }
        return context;
    }

    @Override
    public Future<Void> persist(SessionContext context) {
        return stateEngine
            .saveSession(context)
            .compose(v -> logManager.appendLog(context));
    }

    @Override
    public SessionContext createSession(String sessionId) {
        ModelOptions options = new ModelOptions(
            configManager.getTemperature(),
            configManager.getMaxTokens(),
            configManager.getModel(),
            configManager.isStream()
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

    @Override
    public void addSteeringMessage(String sessionId, String message) {
        steeringQueues.computeIfAbsent(sessionId, k -> new ConcurrentLinkedQueue<>()).add(message);
    }

    @Override
    public List<String> pollSteeringMessages(String sessionId) {
        ConcurrentLinkedQueue<String> queue = steeringQueues.get(sessionId);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> messages = new ArrayList<>();
        String msg;
        while ((msg = queue.poll()) != null) {
            messages.add(msg);
        }
        return messages;
    }
}
