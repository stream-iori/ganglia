package work.ganglia.port.internal.memory;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.model.CompressionContext;

/** Port for Real-time Tool Output Compression. */
public interface ObservationCompressor {
  boolean requiresCompression(String rawOutput);

  Future<String> compress(String rawOutput, CompressionContext context);
}
