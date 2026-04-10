package work.ganglia.infrastructure.external.tool;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.util.PathMapper;
import work.ganglia.util.PathSanitizer;

/** Built-in tools for local filesystem search using native system commands. */
public class BashFileSystemTools implements ToolSet {
  private static final Logger logger = LoggerFactory.getLogger(BashFileSystemTools.class);

  private final CommandExecutor commandExecutor;
  private final PathMapper pathMapper;

  public BashFileSystemTools(CommandExecutor commandExecutor) {
    this(commandExecutor, new PathSanitizer());
  }

  public BashFileSystemTools(CommandExecutor commandExecutor, PathMapper pathMapper) {
    this.commandExecutor = commandExecutor;
    this.pathMapper = pathMapper;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "grep_search",
            "Search for a pattern in files within a directory",
            """
                {
                  "type": "object",
                  "properties": {
                    "path": { "type": "string", "description": "The directory path to search in" },
                    "pattern": { "type": "string", "description": "The regex pattern to search for" },
                    "include": { "type": "string", "description": "Optional glob pattern for files to include (e.g. *.java)" }
                  },
                  "required": ["path", "pattern"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    try {
      if ("grep_search".equals(toolName)) {
        return grepSearch(args);
      }
      return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    } catch (SecurityException | IllegalArgumentException e) {
      logger.warn("[SANDBOX_VIOLATION] Tool: {}, Error: {}", toolName, e.getMessage());
      return Future.succeededFuture(
          ToolInvokeResult.error("Security/Validation Error: " + e.getMessage()));
    }
  }

  private Future<ToolInvokeResult> grepSearch(Map<String, Object> args) {
    String path = pathMapper.map((String) args.get("path"));
    String pattern = (String) args.get("pattern");
    String include = (String) args.get("include");

    StringBuilder sb = new StringBuilder("grep -rnE ");
    sb.append("--exclude-dir=.git ")
        .append("--exclude-dir=node_modules ")
        .append("--exclude-dir=target ")
        .append("--exclude-dir=venv ")
        .append("--exclude-dir=.venv ")
        .append("--exclude-dir=__pycache__ ");

    if (include != null && !include.isEmpty()) {
      sb.append("--include=").append(PathSanitizer.escapeShellArg(include)).append(" ");
    }

    sb.append(PathSanitizer.escapeShellArg(pattern)).append(" ");
    sb.append(PathSanitizer.escapeShellArg(path));

    return execute("grep_search", sb.toString());
  }

  private Future<ToolInvokeResult> execute(String toolName, String command) {
    logger.debug("[FS_EXEC] Tool: {}, Command: {}", toolName, command);

    return commandExecutor
        .execute(command, null, null)
        .map(CommandResultHandler::fromResult)
        .recover(err -> CommandResultHandler.recoverError(toolName, err));
  }
}
