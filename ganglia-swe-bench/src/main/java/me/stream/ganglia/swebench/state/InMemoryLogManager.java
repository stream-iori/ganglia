package me.stream.ganglia.swebench.state;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.state.LogManager;

public class InMemoryLogManager implements LogManager {
    @Override
    public Future<Void> appendLog(SessionContext context) {
        return Future.succeededFuture();
    }
}
