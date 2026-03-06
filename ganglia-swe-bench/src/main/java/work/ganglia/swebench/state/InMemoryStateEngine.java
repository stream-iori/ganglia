package work.ganglia.swebench.state;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.StateEngine;
import work.ganglia.kernel.todo.ToDoList;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryStateEngine implements StateEngine {
    private final Map<String, SessionContext> sessions = new HashMap<>();

    @Override
    public Future<SessionContext> loadSession(String sessionId) {
        SessionContext ctx = sessions.get(sessionId);
        if (ctx == null) return Future.failedFuture("Session not found");
        return Future.succeededFuture(ctx);
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
}
