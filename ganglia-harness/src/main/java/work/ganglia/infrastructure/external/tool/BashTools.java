package work.ganglia.infrastructure.external.tool;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;

public class BashTools implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(BashTools.class);

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
    logger.debug("[SHELL_EXEC] Executing: {} in {}", command, dirPath);

    return commandExecutor
        .execute(command, dirPath, executionContext)
        .map(CommandResultHandler::fromResult)
        .recover(err -> CommandResultHandler.recoverError("run_shell_command", err));
  }
}
