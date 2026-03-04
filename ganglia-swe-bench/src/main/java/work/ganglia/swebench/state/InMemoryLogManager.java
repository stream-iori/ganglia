package work.ganglia.swebench.state;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.state.LogManager;

public class InMemoryLogManager implements LogManager {
    @Override
    public Future<Void> appendLog(SessionContext context) {
        return Future.succeededFuture();
    }
}
