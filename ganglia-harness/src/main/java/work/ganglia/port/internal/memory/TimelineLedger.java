package work.ganglia.port.internal.memory;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.internal.memory.model.TimelineEvent;

/** Port for Automated System Timeline/Ledger. */
public interface TimelineLedger {
  Future<Void> appendEvent(TimelineEvent event);

  Future<List<TimelineEvent>> getRecentEvents(int limit);
}
