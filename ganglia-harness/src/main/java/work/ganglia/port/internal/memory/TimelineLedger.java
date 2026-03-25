package work.ganglia.port.internal.memory;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.model.TimelineEvent;

/** Port for Automated System Timeline/Ledger. */
public interface TimelineLedger {
  Future<Void> appendEvent(TimelineEvent event);

  Future<List<TimelineEvent>> getRecentEvents(int limit);
}
