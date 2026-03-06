package work.ganglia.swebench.state;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.LogManager;

public class InMemoryLogManager implements LogManager {
    @Override
    public Future<Void> appendLog(SessionContext context) {
        return Future.succeededFuture();
    }
}
