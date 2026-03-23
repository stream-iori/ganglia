package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Vertx;

/** Factory for creating and managing built-in core tool sets. */
public class ToolsFactory {
  private final Vertx vertx;

  public ToolsFactory(Vertx vertx, String projectRoot) {
    this.vertx = vertx;
  }
}
