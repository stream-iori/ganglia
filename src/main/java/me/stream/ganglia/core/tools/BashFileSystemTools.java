package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolErrorResult;
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

    public Future<String> ls(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute("ls", List.of("ls", "-F", path), DEFAULT_TIMEOUT_MS);
    }

    public Future<String> cat(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute("cat", List.of("cat", path), DEFAULT_TIMEOUT_MS);
    }

    /**
     * Executes a system command with arguments and a timeout.
     * Prevents memory exhaustion by limiting output size to 16MB.
     */
    private Future<String> execute(String toolName, List<String> commandWithArgs, long timeoutMs) {
        return vertx.executeBlocking(() -> {
            Process process = null;
            String partialOutput = "";
            try {
                ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
                pb.redirectErrorStream(true);
                process = pb.start();

                // Custom stream reader to handle limit
                StreamResult streamResult = readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE);
                partialOutput = streamResult.content;

                if (streamResult.limitExceeded) {
                    throw new ToolExecutionException(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit of 16MB", null, partialOutput));
                }

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new ToolExecutionException(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + timeoutMs + "ms", null, partialOutput));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    throw new ToolExecutionException(new ToolErrorResult(
                        toolName, ToolErrorResult.ErrorType.COMMAND_FAILED,
                        "Command failed with exit code " + exitCode, exitCode, partialOutput));
                }

                return partialOutput;
            } catch (ToolExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new ToolExecutionException(new ToolErrorResult(
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
                    // Read what we can up to the limit
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