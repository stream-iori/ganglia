package work.ganglia.port.internal.memory;

import io.vertx.core.Vertx;

import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.external.llm.ModelGateway;

/**
 * Configuration record passed to {@link MemorySystemProvider} to create a {@link MemorySystem}.
 *
 * @param vertx The Vert.x instance.
 * @param projectRoot Absolute path to the project root directory.
 * @param modelGateway The model gateway for LLM-based compression.
 * @param configProvider Model configuration provider.
 * @param compressionModel The model name used for compression tasks.
 * @param storageBackend Optional storage backend name (e.g. "sqlite", "filesystem"). When {@code
 *     null}, the first SPI provider on the classpath is used.
 */
public record MemorySystemConfig(
    Vertx vertx,
    String projectRoot,
    ModelGateway modelGateway,
    ModelConfigProvider configProvider,
    String compressionModel,
    String storageBackend) {

  /** Convenience constructor without storageBackend (defaults to null — auto-detect). */
  public MemorySystemConfig(
      Vertx vertx,
      String projectRoot,
      ModelGateway modelGateway,
      ModelConfigProvider configProvider,
      String compressionModel) {
    this(vertx, projectRoot, modelGateway, configProvider, compressionModel, null);
  }
}
