package work.ganglia.coding.tool.util;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.util.VertxProcess;

/** Executes commands on the local system using Bash. */
public class LocalCommandExecutor implements CommandExecutor {
  private final Vertx vertx;

  public LocalCommandExecutor(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<VertxProcess.Result> execute(
      String command, String workingDir, ExecutionContext context) {

    return VertxProcess.execute(
        vertx,
        List.of("bash", "-c", command),
        workingDir,
        DEFAULT_TIMEOUT_MS,
        MAX_OUTPUT_SIZE,
        chunk -> {
          if (context != null) {
            context.emitStream(chunk);
          }
        });
  }
}
