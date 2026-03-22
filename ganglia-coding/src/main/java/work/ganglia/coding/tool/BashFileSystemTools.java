package work.ganglia.coding.tool;

import io.vertx.core.Future;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolErrorResult;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.PathSanitizer;
import work.ganglia.util.VertxProcess;

/** Built-in tools for local filesystem operations using native system commands. */
public class BashFileSystemTools implements ToolSet {
  private static final Logger log = LoggerFactory.getLogger(BashFileSystemTools.class);

  private final CommandExecutor commandExecutor;
  private final PathSanitizer sanitizer;

  public BashFileSystemTools(CommandExecutor commandExecutor) {
    this(commandExecutor, new PathSanitizer());
  }

  public BashFileSystemTools(CommandExecutor commandExecutor, PathSanitizer sanitizer) {
    this.commandExecutor = commandExecutor;
    this.sanitizer = sanitizer;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "list_directory",
            "List files in a directory using bash ls",
            """
                {
                  "type": "object",
                  "properties": {
                    "path": {
                      "type": "string",
                      "description": "The directory path to list"
                    }
                  },
                  "required": ["path"]
                }
                """),
        new ToolDefinition(
            "read_file",
            "Read content of a file with line-based pagination",
            """
                {
                  "type": "object",
                  "properties": {
                    "path": { "type": "string", "description": "The file path to read" },
                    "offset": { "type": "integer", "description": "0-based line index to start reading from. Defaults to 0.", "default": 0 },
                    "limit": { "type": "integer", "description": "Maximum number of lines to read. Defaults to 500.", "default": 500 }
                  },
                  "required": ["path"]
                }
                """),
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
                """),
        new ToolDefinition(
            "read_files",
            "Read multiple files at once",
            """
                {
                  "type": "object",
                  "properties": {
                    "paths": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of file paths to read"
                    },
                    "limit_per_file": {
                      "type": "integer",
                      "description": "Maximum number of lines to read per file. Defaults to 300.",
                      "default": 300
                    }
                  },
                  "required": ["paths"]
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
      return switch (toolName) {
        case "list_directory" -> ls(args);
        case "read_file" -> cat(args);
        case "read_files" -> readFiles(args);
        case "grep_search" -> grepSearch(args);
        default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
      };
    } catch (SecurityException | IllegalArgumentException e) {
      log.warn("[SANDBOX_VIOLATION] Tool: {}, Error: {}", toolName, e.getMessage());
      return Future.succeededFuture(
          ToolInvokeResult.error("Security/Validation Error: " + e.getMessage()));
    }
  }

  private Future<ToolInvokeResult> ls(Map<String, Object> args) {
    String path = sanitizer.sanitize((String) args.get("path"));
    return execute("list_directory", "ls -F " + PathSanitizer.escapeShellArg(path));
  }

  public Future<ToolInvokeResult> cat(Map<String, Object> args) {
    String rawPath = (String) args.get("path");
    String safePath = sanitizer.sanitize(rawPath);
    int offset = ((Number) args.getOrDefault("offset", 0)).intValue();
    int limit = ((Number) args.getOrDefault("limit", 500)).intValue();

    String escapedPath = PathSanitizer.escapeShellArg(safePath);

    String script =
        """
            file=%s
            if [ ! -f "$file" ]; then
              echo "File not found: $file" >&2
              exit 1
            fi
            offset=%d
            limit=%d
            total=$(wc -l < "$file" | tr -d ' ')
            start=$((offset + 1))
            end=$((offset + limit))

            sed -n "${start},${end}p" "$file"
            echo ""

            if [ $end -lt $total ]; then actual_end=$end; else actual_end=$total; fi
            echo "--- [Lines $offset to $actual_end of $total] ---"

            if [ $end -lt $total ]; then
              echo "Hint: More lines available. Use 'read_file' with 'offset: $end' to read more."
            fi
            """
            .formatted(escapedPath, offset, limit);

    return execute("read_file", script);
  }

  private Future<ToolInvokeResult> readFiles(Map<String, Object> args) {
    List<String> paths = (List<String>) args.get("paths");
    if (paths == null || paths.isEmpty()) {
      return Future.succeededFuture(ToolInvokeResult.error("No paths provided"));
    }

    int limitPerFile = ((Number) args.getOrDefault("limit_per_file", 300)).intValue();

    // Sanitize and escape all paths
    String escapedPaths =
        paths.stream()
            .map(sanitizer::sanitize)
            .map(PathSanitizer::escapeShellArg)
            .collect(Collectors.joining(" "));

    String script =
        """
            limit=%d
            files=(%s)
            for f in "${files[@]}"; do
              echo "--- FILE: $f ---"
              if [ -f "$f" ]; then
                total=$(wc -l < "$f" | tr -d ' ')
                head -n "$limit" "$f"
                if [ "$total" -gt "$limit" ]; then
                  echo ""
                  echo "--- [TRUNCATED: $limit of $total lines shown. Use 'read_file' for full content.] ---"
                fi
              else
                echo "[ERROR: File not found or not a regular file]"
              fi
              echo ""
            done
            """
            .formatted(limitPerFile, escapedPaths);

    return execute("read_files", script);
  }

  private Future<ToolInvokeResult> grepSearch(Map<String, Object> args) {
    String path = sanitizer.sanitize((String) args.get("path"));
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
    log.debug("[FS_EXEC] Tool: {}, Command: {}", toolName, command);

    return commandExecutor
        .execute(command, null, null)
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
                ToolErrorResult.ErrorType type =
                    msg.contains("Output size limit exceeded")
                        ? ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED
                        : ToolErrorResult.ErrorType.TIMEOUT;
                return Future.succeededFuture(
                    ToolInvokeResult.exception(
                        new ToolErrorResult(toolName, type, msg, null, ee.getPartialOutput())));
              }
              return Future.succeededFuture(ToolInvokeResult.error(err.getMessage()));
            });
  }
}
