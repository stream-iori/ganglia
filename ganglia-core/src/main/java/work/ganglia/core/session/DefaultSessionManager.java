package work.ganglia.core.session;

import io.vertx.core.Future;
import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.model.*;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.Turn;
import work.ganglia.core.state.LogManager;
import work.ganglia.core.state.StateEngine;
import work.ganglia.memory.ContextCompressor;
import work.ganglia.tools.model.ToDoList;

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
                configManager.getModel()
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

    @Override
    public Future<SessionContext> compressSession(SessionContext context, int turnsToKeep, ContextCompressor compressor) {
        List<Turn> allPrevious = context.previousTurns();
        if (allPrevious.size() <= turnsToKeep) {
            return Future.succeededFuture(context);
        }

        int compressCount = allPrevious.size() - turnsToKeep;
        List<Turn> toCompress = allPrevious.subList(0, compressCount);
        List<Turn> toKeep = new ArrayList<>(allPrevious.subList(compressCount, allPrevious.size()));

        return compressor.compress(toCompress)
            .map(summary -> {
                Message summaryMsg = Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + summary);
                Turn summaryTurn = Turn.newTurn("summary-" + System.currentTimeMillis(), summaryMsg);

                List<Turn> newPrevious = new ArrayList<>();
                newPrevious.add(summaryTurn);
                newPrevious.addAll(toKeep);

                return context.withPreviousTurns(newPrevious);
            });
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
