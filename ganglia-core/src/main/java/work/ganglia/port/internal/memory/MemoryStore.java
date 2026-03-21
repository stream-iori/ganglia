package work.ganglia.port.internal.memory;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.internal.memory.model.*;

/** Port for Long-term Memory Storage and Retrieval (Hybrid Search & Progressive Disclosure). */
public interface MemoryStore {
  Future<Void> store(MemoryEntry entry);

  Future<List<MemoryEntry>> search(MemoryQuery query);

  Future<List<MemoryIndexItem>> getRecentIndex(int limit);

  Future<MemoryEntry> recall(String id);
}
