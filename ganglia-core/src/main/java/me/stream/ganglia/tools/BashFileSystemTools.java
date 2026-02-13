package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolErrorResult;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Built-in tools for local filesystem operations using native system commands.
 */
public class BashFileSystemTools implements ToolSet {
    private static final Logger log = LoggerFactory.getLogger(BashFileSystemTools.class);
    private static final long MAX_OUTPUT_SIZE = 1 * 1024 * 1024; // 1MB
    private static final long MAX_FILE_SIZE = 1 * 1024 * 1024; // 1MB
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds

    private final Vertx vertx;

    public BashFileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("list_directory", "List files in a directory using bash ls",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The directory path to list\"\n    }\n  },\n  \"required\": [\"path\"]\n}"),
            new ToolDefinition("read_file", "Read content of a file using bash cat",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"path\": {\n      \"type\": \"string\",\n      \"description\": \"The file path to read\"\n    }\n  },\n  \"required\": [\"path\"]\n}"),
            new ToolDefinition("grep_search", "Search for a pattern in files within a directory",
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
            new ToolDefinition("glob", "Find files matching a pattern",
                """
                {
                  "type": "object",
                  "properties": {
                    "path": { "type": "string", "description": "The base directory to start search" },
                    "pattern": { "type": "string", "description": "The glob pattern (e.g. **/*.java)" }
                  },
                  "required": ["path", "pattern"]
                }
                """)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, me.stream.ganglia.core.model.SessionContext context) {
        return switch (toolName) {
            case "list_directory" -> ls(args);
            case "read_file" -> cat(args);
            case "grep_search" -> grepSearch(args);
            case "glob" -> glob(args);
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
        };
    }

    private Future<ToolInvokeResult> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute("ls", List.of("ls", "-F", path), DEFAULT_TIMEOUT_MS);
    }

    public Future<ToolInvokeResult> cat(Map<String, Object> args) {
        String path = (String) args.get("path");
        return vertx.fileSystem().props(path)
            .compose(props -> {
                if (props.size() > MAX_FILE_SIZE) {
                    return Future.succeededFuture(ToolInvokeResult.error(
                        "File is too large: " + props.size() + " bytes. Max allowed is " + MAX_FILE_SIZE + " bytes."));
                }
                return execute("cat", List.of("cat", path), DEFAULT_TIMEOUT_MS);
            })
            .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Error checking file properties: " + err.getMessage())));
    }

    private Future<ToolInvokeResult> grepSearch(Map<String, Object> args) {
        String path = (String) args.get("path");
        String pattern = (String) args.get("pattern");
        String include = (String) args.get("include");

        List<String> command = new java.util.ArrayList<>(List.of("grep", "-rnE", pattern, path));
        if (include != null && !include.isEmpty()) {
            command.add("--include=" + include);
        }
        return execute("grep_search", command, DEFAULT_TIMEOUT_MS);
    }

    private Future<ToolInvokeResult> glob(Map<String, Object> args) {
        String path = (String) args.get("path");
        String pattern = (String) args.get("pattern");

        // Convert simple glob to find command
        // Note: This is a simplified version. For complex globs, we might need a better parser.
        // But for common cases like **/*.java, we can handle it.
        String findPattern = pattern.replace("**/", "");
        List<String> command = List.of("find", path, "-name", findPattern);

        return execute("glob", command, DEFAULT_TIMEOUT_MS);
    }

    private Future<ToolInvokeResult> execute(String toolName, List<String> commandWithArgs, long timeoutMs) {
        log.debug("[FS_EXEC] Tool: {}, Command: {}", toolName, commandWithArgs);
        return vertx.<ToolInvokeResult>executeBlocking(() -> {
            Process process = null;
            String partialOutput = "";
            try {
                ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
                pb.redirectErrorStream(true);
                process = pb.start();

                StreamResult streamResult = readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE);
                partialOutput = streamResult.content;

                if (streamResult.limitExceeded) {
                    log.warn("[FS_LIMIT] Output size exceeded for: {}", toolName);
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit of 1MB", null, partialOutput));
                }

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    log.error("[FS_TIMEOUT] Command timed out: {}", toolName);
                    process.destroyForcibly();
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + timeoutMs + "ms", null, partialOutput));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.debug("[FS_FAIL] Exit code: {}, Tool: {}", exitCode, toolName);
                    return ToolInvokeResult.error("Command failed with exit code " + exitCode + ": " + partialOutput);
                }

                log.debug("[FS_SUCCESS] Tool: {}", toolName);
                return ToolInvokeResult.success(partialOutput);
            } catch (Exception e) {
                log.error("[FS_ERROR] Exception for tool: {}", toolName, e);
                return ToolInvokeResult.exception(new ToolErrorResult(
                    toolName, ToolErrorResult.ErrorType.UNKNOWN,
                    "Execution error: " + e.getMessage(), null, partialOutput));
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }

    private StreamResult readStreamWithLimit(InputStream is, long limit) throws Exception {
        try (BufferedInputStream bis = new BufferedInputStream(is);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            boolean limitExceeded = false;
            while ((bytesRead = bis.read(buffer)) != -1) {
                if (totalRead + bytesRead > limit) {
                    limitExceeded = true;
                    int remaining = (int) (limit - totalRead);
                    if (remaining > 0) {
                        baos.write(buffer, 0, remaining);
                    }
                    break;
                }
                totalRead += bytesRead;
                baos.write(buffer, 0, bytesRead);
            }
            return new StreamResult(baos.toString(StandardCharsets.UTF_8), limitExceeded);
        }
    }

    private record StreamResult(String content, boolean limitExceeded) {}
}
