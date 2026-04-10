package work.ganglia.infrastructure.internal.memory;

import java.nio.file.Paths;

import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.MemorySystemConfig;
import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.util.Constants;

/**
 * SQLite-backed memory provider. Extends {@link AbstractMemorySystemProvider} and supplies SQLite
 * storage implementations; all modules, compressors, and services are inherited.
 */
public class SqliteMemoryProvider extends AbstractMemorySystemProvider {

  /** Provider name used for SPI selection via {@code storageBackend} config. */
  public static final String NAME = "sqlite";

  private SqliteConnectionManager connectionManager;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  protected MemoryStore createMemoryStore(MemorySystemConfig config) {
    return new SqliteMemoryStore(getOrCreateConnectionManager(config));
  }

  @Override
  protected SessionStore createSessionStore(MemorySystemConfig config) {
    return new SqliteSessionStore(getOrCreateConnectionManager(config));
  }

  @Override
  protected DailyRecordManager createDailyRecordManager(MemorySystemConfig config) {
    return new SqliteDailyRecordManager(getOrCreateConnectionManager(config));
  }

  @Override
  protected LongTermMemory createLongTermMemory(MemorySystemConfig config) {
    return new SqliteLongTermMemory(getOrCreateConnectionManager(config));
  }

  @Override
  protected TimelineLedger createTimelineLedger(MemorySystemConfig config) {
    return new SqliteTimelineLedger(getOrCreateConnectionManager(config));
  }

  private synchronized SqliteConnectionManager getOrCreateConnectionManager(
      MemorySystemConfig config) {
    if (connectionManager == null) {
      String dbPath = Paths.get(config.projectRoot(), Constants.DIR_MEMORY, "memory.db").toString();
      connectionManager = new SqliteConnectionManager(config.vertx(), dbPath);
      // Eagerly initialize schema — blocks on first call but subsequent calls are cheap
      connectionManager.initSchema().result();
    }
    return connectionManager;
  }
}
