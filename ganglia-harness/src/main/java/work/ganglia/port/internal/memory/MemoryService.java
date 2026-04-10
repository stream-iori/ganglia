package work.ganglia.port.internal.memory;

import java.util.List;

/**
 * Registry and event dispatcher for memory modules. Implementations listen for memory events and
 * delegate to registered modules.
 */
public interface MemoryService {

  void registerModule(MemoryModule module);

  List<MemoryModule> getModules();
}
