package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.state.LogManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InMemoryLogManager implements LogManager {

    private final List<SessionContext> logs = Collections.synchronizedList(new ArrayList<>());

    @Override
    public Future<Void> appendLog(SessionContext context) {
        logs.add(context);
        return Future.succeededFuture();
    }

    // Test helper
    public List<SessionContext> getLogs() {
        return logs;
    }
}
