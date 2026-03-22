package work.ganglia.coding.tool;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.VertxProcess;

public class BashTools implements ToolSet {
  private static final Logger log = LoggerFactory.getLogger(BashTools.class);

  private final CommandExecutor commandExecutor;

  public BashTools(CommandExecutor commandExecutor) {
    this.commandExecutor = commandExecutor;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "run_shell_command",
            "Execute arbitrary bash commands",
            """
                {
                  "type": "object",
                  "properties": {
                    "command": {
                      "type": "string",
                      "description": "The command to execute"
                    },
                    "dir_path": {
                      "type": "string",
                      "description": "The directory to run the command in (optional)"
                    }
                  },
                  "required": ["command"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    if ("run_shell_command".equals(toolName)) {
      String command = (String) args.get("command");
      String dirPath = (String) args.get("dir_path");
      return runShellCommand(command, dirPath, executionContext);
    }
    return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
  }

  private Future<ToolInvokeResult> runShellCommand(
      String command,
      String dirPath,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    log.debug("[SHELL_EXEC] Executing: {} in {}", command, dirPath);

    return commandExecutor
        .execute(command, dirPath, executionContext)
        .map(
            result -> {
              if (result.exitCode() != 0) {
                return ToolInvokeResult.error(
                    "Command failed with exit code " + result.exitCode() + ": " + result.output());
              }
              return ToolInvokeResult.success(result.output());
            })
        .recover(
            err -> {
              if (err instanceof VertxProcess.ExecutionException ee) {
                String msg = ee.getMessage();
                work.ganglia.infrastructure.external.tool.model.ToolErrorResult.ErrorType type =
                    msg.contains("Output size limit exceeded")
                        ? work.ganglia.infrastructure.external.tool.model.ToolErrorResult.ErrorType
                            .SIZE_LIMIT_EXCEEDED
                        : work.ganglia.infrastructure.external.tool.model.ToolErrorResult.ErrorType
                            .TIMEOUT;
                return Future.succeededFuture(
                    ToolInvokeResult.exception(
                        new work.ganglia.infrastructure.external.tool.model.ToolErrorResult(
                            "run_shell_command", type, msg, null, ee.getPartialOutput())));
              }
              return Future.succeededFuture(ToolInvokeResult.error(err.getMessage()));
            });
  }
}
