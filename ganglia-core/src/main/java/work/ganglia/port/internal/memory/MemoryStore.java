package work.ganglia.port.internal.memory;

import io.vertx.core.Future;
import work.ganglia.port.internal.memory.model.*;
import java.util.List;

/**
 * Port for Long-term Memory Storage and Retrieval (Hybrid Search & Progressive Disclosure).
 */
public interface MemoryStore {
    Future<Void> store(MemoryEntry entry);
    Future<List<MemoryEntry>> search(MemoryQuery query);
    Future<List<MemoryIndexItem>> getRecentIndex(int limit);
    Future<MemoryEntry> recall(String id);
}