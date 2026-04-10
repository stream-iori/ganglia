package work.ganglia.port.internal.memory;

import java.util.List;

import work.ganglia.port.internal.prompt.ContextSource;

/**
 * The assembled memory system returned by {@link MemorySystemProvider}. Contains all memory
 * infrastructure components ready for use.
 *
 * @param memoryStore The memory store for observation persistence.
 * @param observationCompressor Compressor for raw tool output.
 * @param contextCompressor Compressor for conversation context.
 * @param timelineLedger Timeline event ledger.
 * @param dailyRecordManager Manager for daily record persistence.
 * @param longTermMemory Long-term knowledge base.
 * @param memoryService Registry and event dispatcher for memory modules.
 * @param memoryContextSource Context source providing memory index fragments.
 * @param sessionStore Store for session records and cross-session search.
 * @param defaultModules Default memory modules registered by the provider.
 */
public record MemorySystem(
    MemoryStore memoryStore,
    ObservationCompressor observationCompressor,
    ContextCompressor contextCompressor,
    TimelineLedger timelineLedger,
    DailyRecordManager dailyRecordManager,
    LongTermMemory longTermMemory,
    MemoryService memoryService,
    ContextSource memoryContextSource,
    SessionStore sessionStore,
    List<MemoryModule> defaultModules) {}
