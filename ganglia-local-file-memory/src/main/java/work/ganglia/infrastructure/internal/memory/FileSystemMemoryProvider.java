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
 * File-system-based memory provider. Extends {@link AbstractMemorySystemProvider} and supplies
 * filesystem storage implementations; all modules, compressors, and services are inherited.
 */
public class FileSystemMemoryProvider extends AbstractMemorySystemProvider {

  public static final String NAME = "filesystem";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  protected MemoryStore createMemoryStore(MemorySystemConfig config) {
    return new FileSystemMemoryStore(config.vertx(), config.projectRoot());
  }

  @Override
  protected SessionStore createSessionStore(MemorySystemConfig config) {
    return new FileSystemSessionStore(
        config.vertx(), Paths.get(config.projectRoot(), Constants.DIR_MEMORY).toString());
  }

  @Override
  protected DailyRecordManager createDailyRecordManager(MemorySystemConfig config) {
    return new FileSystemDailyRecordManager(
        config.vertx(), Paths.get(config.projectRoot(), Constants.DIR_MEMORY).toString());
  }

  @Override
  protected LongTermMemory createLongTermMemory(MemorySystemConfig config) {
    return new FileSystemLongTermMemory(
        config.vertx(), Paths.get(config.projectRoot(), Constants.FILE_MEMORY_MD).toString());
  }

  @Override
  protected TimelineLedger createTimelineLedger(MemorySystemConfig config) {
    return new MarkdownTimelineLedger(config.vertx(), config.projectRoot());
  }
}
