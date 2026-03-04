package work.ganglia.core.state;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;

public interface LogManager {
    /**
     * Appends the latest updates from the session context to the daily log.
     */
    Future<Void> appendLog(SessionContext context);
}
