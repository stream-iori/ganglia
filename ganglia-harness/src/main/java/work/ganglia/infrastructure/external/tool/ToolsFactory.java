package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Vertx;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.LongTermMemory;

/** Factory for creating and managing built-in core tool sets. */
public class ToolsFactory {
  private final Vertx vertx;
  private final InteractionTools interactionTools;

  public ToolsFactory(
      Vertx vertx,
      ContextCompressor compressor,
      LongTermMemory longTermMemory,
      String projectRoot) {
    this.vertx = vertx;
    this.interactionTools = new InteractionTools(vertx);
  }

  public InteractionTools getInteractionTools() {
    return interactionTools;
  }
}
