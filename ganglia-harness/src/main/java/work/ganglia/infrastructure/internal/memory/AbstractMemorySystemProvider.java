package work.ganglia.infrastructure.internal.memory;

import java.util.ArrayList;
import java.util.List;

import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.DailyRecordManager;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.memory.MemoryStore;
import work.ganglia.port.internal.memory.MemorySystem;
import work.ganglia.port.internal.memory.MemorySystemConfig;
import work.ganglia.port.internal.memory.MemorySystemProvider;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.port.internal.prompt.ContextSource;

/**
 * Template-method base class for {@link MemorySystemProvider}. Subclasses supply the 5 storage
 * implementations; this class provides default wiring for compressors, event service, context
 * source, and memory modules.
 *
 * <p>Override any {@code createXxx} method to customise the default behaviour.
 */
public abstract class AbstractMemorySystemProvider implements MemorySystemProvider {

  @Override
  public final MemorySystem create(MemorySystemConfig config) {
    // 1. Storage backends — subclass must provide
    MemoryStore memoryStore = createMemoryStore(config);
    SessionStore sessionStore = createSessionStore(config);
    DailyRecordManager dailyRecordManager = createDailyRecordManager(config);
    LongTermMemory longTermMemory = createLongTermMemory(config);
    TimelineLedger timelineLedger = createTimelineLedger(config);

    // 2. Shared components — overridable defaults
    ObservationCompressor observationCompressor = createObservationCompressor(config);
    ContextCompressor contextCompressor = createContextCompressor(config);
    EventBusMemoryService memoryService = createMemoryService(config);
    ContextSource memoryContextSource = createMemoryContextSource(memoryStore);

    // 3. Modules — overridable defaults
    List<MemoryModule> modules =
        createDefaultModules(contextCompressor, dailyRecordManager, longTermMemory, sessionStore);
    for (MemoryModule module : modules) {
      memoryService.registerModule(module);
    }

    return new MemorySystem(
        memoryStore,
        observationCompressor,
        contextCompressor,
        timelineLedger,
        dailyRecordManager,
        longTermMemory,
        memoryService,
        memoryContextSource,
        sessionStore,
        List.copyOf(modules));
  }

  // ---- Abstract: storage backends ----

  protected abstract MemoryStore createMemoryStore(MemorySystemConfig config);

  protected abstract SessionStore createSessionStore(MemorySystemConfig config);

  protected abstract DailyRecordManager createDailyRecordManager(MemorySystemConfig config);

  protected abstract LongTermMemory createLongTermMemory(MemorySystemConfig config);

  protected abstract TimelineLedger createTimelineLedger(MemorySystemConfig config);

  // ---- Overridable defaults ----

  protected ObservationCompressor createObservationCompressor(MemorySystemConfig config) {
    int threshold =
        config.configProvider() != null
            ? config.configProvider().getObservationCompressionThreshold()
            : ModelConfigProvider.DEFAULT_OBSERVATION_COMPRESSION_THRESHOLD;
    return new LLMObservationCompressor(
        config.modelGateway(), threshold, config.compressionModel());
  }

  protected ContextCompressor createContextCompressor(MemorySystemConfig config) {
    return new DefaultContextCompressor(config.modelGateway(), config.configProvider());
  }

  protected EventBusMemoryService createMemoryService(MemorySystemConfig config) {
    return new EventBusMemoryService(config.vertx());
  }

  protected ContextSource createMemoryContextSource(MemoryStore memoryStore) {
    return new MemoryIndexContextSource(memoryStore);
  }

  protected List<MemoryModule> createDefaultModules(
      ContextCompressor contextCompressor,
      DailyRecordManager dailyRecordManager,
      LongTermMemory longTermMemory,
      SessionStore sessionStore) {
    List<MemoryModule> modules = new ArrayList<>();
    modules.add(new DailyJournalModule(contextCompressor, dailyRecordManager));
    modules.add(new LongTermKnowledgeModule(longTermMemory));
    modules.add(new UserProfileModule(longTermMemory));
    modules.add(new MemoryNudgeModule());
    modules.add(new SessionPersistenceModule(sessionStore));
    modules.add(new MemoryConsolidationModule(longTermMemory));
    return modules;
  }
}
