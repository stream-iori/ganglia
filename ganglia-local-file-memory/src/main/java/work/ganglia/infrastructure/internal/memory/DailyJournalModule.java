package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.prompt.ContextFragment;

/** Memory module responsible for recording daily accomplishments. */
public class DailyJournalModule implements MemoryModule {
  private final ContextCompressor compressor;
  private final DailyRecordManager dailyRecordManager;

  public DailyJournalModule(ContextCompressor compressor, DailyRecordManager dailyRecordManager) {
    this.compressor = compressor;
    this.dailyRecordManager = dailyRecordManager;
  }

  @Override
  public String id() {
    return "daily-journal";
  }

  @Override
  public Future<List<ContextFragment>> provideContext(SessionContext context) {
    return Future.succeededFuture(Collections.emptyList());
  }

  @Override
  public Future<Void> onEvent(MemoryEvent event) {
    if (event.type() == MemoryEvent.EventType.TURN_COMPLETED) {
      if (event.turn() == null) {
        return Future.succeededFuture();
      }

      return compressor
          .reflect(event.turn())
          .compose(summary -> dailyRecordManager.record(event.sessionId(), event.goal(), summary));
    }
    return Future.succeededFuture();
  }
}
