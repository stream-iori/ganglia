package work.ganglia.port.external.tool;

import io.vertx.core.Vertx;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.LongTermMemory;

/**
 * Provides a mechanism to lazily instantiate ToolSets that require core dependencies like
 * ContextCompressor or LongTermMemory.
 */
@FunctionalInterface
public interface ToolSetProvider {
  ToolSet create(
      Vertx vertx, ContextCompressor compressor, LongTermMemory longTermMemory, String projectRoot);
}
