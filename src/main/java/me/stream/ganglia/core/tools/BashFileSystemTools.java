package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolErrorResult;
import me.stream.ganglia.core.model.ToolInvokeResult;
import me.stream.ganglia.core.model.ToolType;

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
public class BashFileSystemTools {
    private static final long MAX_OUTPUT_SIZE = 16 * 1024 * 1024; // 16MB
    private static final long DEFAULT_TIMEOUT_MS = 30000; // 30 seconds

    private final Vertx vertx;

    public BashFileSystemTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ls", "List files in a directory using bash ls", 
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
                """, 
                ToolType.BUILTIN),
            new ToolDefinition("cat", "Read content of a file using bash cat", 
                """
                {
                  "type": "object",
                  "properties": {
                    "path": {
                      "type": "string",
                      "description": "The file path to read"
                    }
                  },
                  "required": ["path"]
                }
                """, 
                ToolType.BUILTIN)
        );
    }

    public Future<ToolInvokeResult> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute("ls", List.of("ls", "-F", path), DEFAULT_TIMEOUT_MS);
    }

    public Future<ToolInvokeResult> cat(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute("cat", List.of("cat", path), DEFAULT_TIMEOUT_MS);
    }

    private Future<ToolInvokeResult> execute(String toolName, List<String> commandWithArgs, long timeoutMs) {
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
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit of 16MB", null, partialOutput));
                }

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + timeoutMs + "ms", null, partialOutput));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    // This is a successful invocation but command failed, so we treat as ERROR status
                    return ToolInvokeResult.error("Command failed with exit code " + exitCode + ": " + partialOutput);
                }

                return ToolInvokeResult.success(partialOutput);
            } catch (Exception e) {
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

    private static record StreamResult(String content, boolean limitExceeded) {}
}
