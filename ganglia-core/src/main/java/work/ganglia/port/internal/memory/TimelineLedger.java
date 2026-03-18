package work.ganglia.port.internal.memory;

import io.vertx.core.Future;
import work.ganglia.port.internal.memory.model.TimelineEvent;
import java.util.List;

/**
 * Port for Automated System Timeline/Ledger.
 */
public interface TimelineLedger {
    Future<Void> appendEvent(TimelineEvent event);
    Future<List<TimelineEvent>> getRecentEvents(int limit);
}