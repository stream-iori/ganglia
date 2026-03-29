package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.tool.model.ToolErrorResult;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.util.VertxProcess;

/** Shared helper for mapping command execution results to {@link ToolInvokeResult}. */
public final class CommandResultHandler {

  private CommandResultHandler() {}

  /** Maps a successful command result to a tool result, treating non-zero exit codes as errors. */
  public static ToolInvokeResult fromResult(VertxProcess.Result result) {
    if (result.exitCode() != 0) {
      return ToolInvokeResult.error(
          "Command failed with exit code " + result.exitCode() + ": " + result.output());
    }
    return ToolInvokeResult.success(result.output());
  }

  /**
   * Recovers from command execution errors, distinguishing size-limit and timeout errors from
   * generic failures.
   */
  public static Future<ToolInvokeResult> recoverError(String toolName, Throwable err) {
    if (err instanceof VertxProcess.ExecutionException ee) {
      String msg = ee.getMessage();
      ToolErrorResult.ErrorType type =
          msg.contains("Output size limit exceeded")
              ? ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED
              : ToolErrorResult.ErrorType.TIMEOUT;
      return Future.succeededFuture(
          ToolInvokeResult.exception(
              new ToolErrorResult(toolName, type, msg, null, ee.getPartialOutput())));
    }
    return Future.succeededFuture(ToolInvokeResult.error(err.getMessage()));
  }
}
