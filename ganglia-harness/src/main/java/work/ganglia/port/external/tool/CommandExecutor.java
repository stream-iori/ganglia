package work.ganglia.port.external.tool;

import io.vertx.core.Future;

import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.util.VertxProcess;

/** Interface for executing shell commands in different environments (Local, Docker, etc.) */
public interface CommandExecutor {

  /**
   * Executes a command.
   *
   * @param command The command to execute
   * @param workingDir The directory to run the command in (can be null for default)
   * @param context The execution context for streaming observations
   * @return A future that completes with the result
   */
  Future<VertxProcess.Result> execute(String command, String workingDir, ExecutionContext context);

  /** Default timeout for commands. */
  long DEFAULT_TIMEOUT_MS = 60000;

  /** Default maximum output size for commands. */
  long MAX_OUTPUT_SIZE = 128 * 1024;
}
