package work.ganglia.coding.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.tool.model.ToolErrorResult;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.util.PathSanitizer;

/** Built-in tools for local filesystem operations using native system commands. */
public class BashFileSystemTools implements ToolSet {
  private static final Logger log = LoggerFactory.getLogger(BashFileSystemTools.class);
  private static final long MAX_OUTPUT_SIZE = 64 * 1024; // 64KB
  private static final long DEFAULT_TIMEOUT_MS = 60000; // 60 seconds

  private final Vertx vertx;
  private final PathSanitizer sanitizer;

  public BashFileSystemTools(Vertx vertx) {
    this(vertx, new PathSanitizer());
  }

  public BashFileSystemTools(Vertx vertx, PathSanitizer sanitizer) {
    this.vertx = vertx;
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
    return execute("ls", List.of("ls", "-F", path), DEFAULT_TIMEOUT_MS);
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

    return vertx
        .fileSystem()
        .exists(safePath)
        .compose(
            exists -> {
              if (!exists) {
                return Future.succeededFuture(
                    ToolInvokeResult.error("File not found: " + safePath));
              }
              return execute("read_file", List.of("bash", "-c", script), DEFAULT_TIMEOUT_MS);
            })
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Error reading file: " + err.getMessage())));
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
                head -n "$limit" "$f"
                total=$(wc -l < "$f" | tr -d ' ')
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

    return execute("read_files", List.of("bash", "-c", script), DEFAULT_TIMEOUT_MS);
  }

  private Future<ToolInvokeResult> grepSearch(Map<String, Object> args) {
    String path = sanitizer.sanitize((String) args.get("path"));
    String pattern = (String) args.get("pattern");
    String include = (String) args.get("include");

    List<String> command =
        new ArrayList<>(
            List.of(
                "grep",
                "-rnE",
                "--exclude-dir=.git",
                "--exclude-dir=node_modules",
                "--exclude-dir=target",
                "--exclude-dir=venv",
                "--exclude-dir=.venv",
                "--exclude-dir=__pycache__",
                pattern,
                path));
    if (include != null && !include.isEmpty()) {
      command.add("--include=" + include);
    }
    return execute("grep_search", command, DEFAULT_TIMEOUT_MS);
  }

  private Future<ToolInvokeResult> execute(
      String toolName, List<String> commandWithArgs, long timeoutMs) {
    log.debug("[FS_EXEC] Tool: {}, Command: {}", toolName, commandWithArgs);
    return vertx.executeBlocking(
        () -> {
          Process process = null;
          try {
            ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
            pb.redirectErrorStream(true);
            process = pb.start();

            final Process p = process;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final boolean[] limitExceeded = {false};

            Thread readerThread =
                new Thread(
                    () -> {
                      try (InputStream is = p.getInputStream()) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = is.read(buffer)) != -1) {
                          if (baos.size() + n > MAX_OUTPUT_SIZE) {
                            limitExceeded[0] = true;
                            int remaining = (int) (MAX_OUTPUT_SIZE - baos.size());
                            if (remaining > 0) {
                              baos.write(buffer, 0, remaining);
                            }
                            break;
                          }
                          baos.write(buffer, 0, n);
                        }
                      } catch (IOException ignored) {
                      }
                    });
            readerThread.setDaemon(true);
            readerThread.start();

            boolean finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
              log.error("[FS_TIMEOUT] Command timed out: {}", toolName);
              p.destroyForcibly();
            }

            // Wait for reader to catch up and exit
            readerThread.join(1000);
            String partialOutput = baos.toString(StandardCharsets.UTF_8);

            if (limitExceeded[0]) {
              log.warn("[FS_LIMIT] Output size exceeded for: {}", toolName);
              p.destroyForcibly();
              return ToolInvokeResult.exception(
                  new ToolErrorResult(
                      toolName,
                      ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                      "Output size exceeded limit of " + (MAX_OUTPUT_SIZE / 1024) + "KB",
                      null,
                      partialOutput));
            }

            if (!finished) {
              return ToolInvokeResult.exception(
                  new ToolErrorResult(
                      toolName,
                      ToolErrorResult.ErrorType.TIMEOUT,
                      "Command timed out after " + timeoutMs + "ms",
                      null,
                      partialOutput));
            }

            int exitCode = p.exitValue();
            if (exitCode != 0) {
              log.debug("[FS_FAIL] Exit code: {}, Tool: {}", exitCode, toolName);
              return ToolInvokeResult.error(
                  "Command failed with exit code " + exitCode + ": " + partialOutput);
            }

            log.debug("[FS_SUCCESS] Tool: {}", toolName);
            return ToolInvokeResult.success(partialOutput);
          } catch (Exception e) {
            log.error("[FS_ERROR] Exception for tool: {}", toolName, e);
            return ToolInvokeResult.exception(
                new ToolErrorResult(
                    toolName,
                    ToolErrorResult.ErrorType.UNKNOWN,
                    "Execution error: " + e.getMessage(),
                    null,
                    ""));
          } finally {
            if (process != null && process.isAlive()) {
              process.destroyForcibly();
            }
          }
        });
  }
}
