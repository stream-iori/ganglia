package work.ganglia.infrastructure.external.tool.util;

import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.util.ProcessOptions;
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

    ProcessOptions options = new ProcessOptions(workingDir, DEFAULT_TIMEOUT_MS, MAX_OUTPUT_SIZE);

    return VertxProcess.execute(
        vertx,
        List.of("bash", "-c", command),
        options,
        chunk -> {
          if (context != null) {
            context.emitStream(chunk);
          }
        });
  }
}
