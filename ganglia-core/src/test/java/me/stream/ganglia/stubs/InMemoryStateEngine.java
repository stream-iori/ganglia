package me.stream.ganglia.stubs;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.state.StateEngine;
import me.stream.ganglia.tools.model.ToDoList;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryStateEngine implements StateEngine {

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    @Override
    public Future<SessionContext> loadSession(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            return Future.succeededFuture(sessions.get(sessionId));
        } else {
            return Future.failedFuture("Session not found: " + sessionId);
        }
    }

    @Override
    public Future<Void> saveSession(SessionContext context) {
        sessions.put(context.sessionId(), context);
        return Future.succeededFuture();
    }

    @Override
    public SessionContext createSession() {
        return new SessionContext(
                UUID.randomUUID().toString(),
                Collections.emptyList(),
                null,
                Collections.emptyMap(),
                Collections.emptyList(),
                null,
                ToDoList.empty()
        );
    }
    
    // Test helper
    public Map<String, SessionContext> getSessions() {
        return sessions;
    }
}
