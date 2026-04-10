package work.ganglia.kernel.hook.builtin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.internal.hook.AgentInterceptor;

/**
 * Pre-turn interceptor that runs environment checks before each Manager's first turn. Implements
 * the Self-Healing Law from the team-based orchestration design.
 *
 * <p>Checks include:
 *
 * <ul>
 *   <li>Git status — detect uncommitted artifacts
 *   <li>Orphan worktree detection
 *   <li>Working directory consistency
 * </ul>
 *
 * <p>Failures abort the turn with a descriptive error. The checks are configurable — pass the list
 * of shell commands to run as preflight checks.
 */
public class PreflightInterceptor implements AgentInterceptor {
  private static final Logger logger = LoggerFactory.getLogger(PreflightInterceptor.class);

  private final CommandExecutor commandExecutor;
  private final List<String> preflightCommands;

  /**
   * @param commandExecutor executor for running shell commands
   * @param preflightCommands list of shell commands to run as preflight checks. Each command must
   *     exit 0 with empty stdout to pass. Non-zero exit or non-empty output means failure.
   */
  public PreflightInterceptor(CommandExecutor commandExecutor, List<String> preflightCommands) {
    this.commandExecutor = commandExecutor;
    this.preflightCommands = preflightCommands;
  }

  @Override
  public Future<SessionContext> preTurn(SessionContext context, String userInput) {
    if (preflightCommands.isEmpty()) {
      return Future.succeededFuture(context);
    }

    logger.info(
        "Running {} preflight checks for session {}",
        preflightCommands.size(),
        context.sessionId());
    return runChecks(context, 0);
  }

  private Future<SessionContext> runChecks(SessionContext context, int index) {
    if (index >= preflightCommands.size()) {
      logger.info("All preflight checks passed for session {}", context.sessionId());
      return Future.succeededFuture(context);
    }

    String command = preflightCommands.get(index);
    logger.debug("Running preflight check [{}]: {}", index, command);

    return commandExecutor
        .execute(command, null, null)
        .compose(
            result -> {
              if (!result.succeeded()) {
                String msg =
                    "Preflight check failed [%s]: exit code %d. Output: %s"
                        .formatted(command, result.exitCode(), result.output());
                logger.warn(msg);
                return Future.failedFuture(new RuntimeException(msg));
              }

              // Non-empty output from porcelain commands means dirty state
              String output = result.output().trim();
              if (!output.isEmpty()) {
                String msg =
                    "Preflight check failed [%s]: unexpected output detected:\n%s"
                        .formatted(command, output);
                logger.warn(msg);
                return Future.failedFuture(new RuntimeException(msg));
              }

              return runChecks(context, index + 1);
            })
        .recover(
            err -> {
              if (err instanceof RuntimeException && err.getMessage().startsWith("Preflight")) {
                return Future.failedFuture(err);
              }
              String msg = "Preflight check failed [%s]: %s".formatted(command, err.getMessage());
              logger.error(msg);
              return Future.failedFuture(new RuntimeException(msg));
            });
  }
}
