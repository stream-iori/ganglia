package work.ganglia.infrastructure.internal.memory;

import java.nio.file.Paths;
import java.util.List;

import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.MemorySystem;
import work.ganglia.port.internal.memory.MemorySystemConfig;
import work.ganglia.port.internal.memory.MemorySystemProvider;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.util.Constants;

/** File-system-based implementation of {@link MemorySystemProvider}. */
public class FileSystemMemoryProvider implements MemorySystemProvider {

  @Override
  public MemorySystem create(MemorySystemConfig config) {
    MemoryStore memoryStore = new FileSystemMemoryStore(config.vertx(), config.projectRoot());

    ObservationCompressor observationCompressor =
        new LLMObservationCompressor(config.modelGateway(), 4000, config.compressionModel());

    TimelineLedger timelineLedger =
        new MarkdownTimelineLedger(config.vertx(), config.projectRoot());

    ContextCompressor contextCompressor =
        new DefaultContextCompressor(config.modelGateway(), config.configProvider());

    DailyRecordManager dailyRecordManager =
        new FileSystemDailyRecordManager(
            config.vertx(), Paths.get(config.projectRoot(), Constants.DIR_MEMORY).toString());

    LongTermMemory longTermMemory =
        new FileSystemLongTermMemory(
            config.vertx(), Paths.get(config.projectRoot(), Constants.FILE_MEMORY_MD).toString());

    EventBusMemoryService memoryService = new EventBusMemoryService(config.vertx());

    DailyJournalModule dailyJournal = new DailyJournalModule(contextCompressor, dailyRecordManager);
    LongTermKnowledgeModule longTermKnowledge = new LongTermKnowledgeModule(longTermMemory);

    memoryService.registerModule(dailyJournal);
    memoryService.registerModule(longTermKnowledge);

    MemoryContextSource memoryContextSource = new MemoryContextSource(memoryStore);

    return new MemorySystem(
        memoryStore,
        observationCompressor,
        contextCompressor,
        timelineLedger,
        dailyRecordManager,
        longTermMemory,
        memoryService,
        memoryContextSource,
        List.of(dailyJournal, longTermKnowledge));
  }
}
