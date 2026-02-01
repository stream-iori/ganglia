package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolType;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        return execute(List.of("ls", "-F", path), DEFAULT_TIMEOUT_MS);
    }

    public Future<String> cat(Map<String, Object> args) {
        String path = (String) args.get("path");
        return execute(List.of("cat", path), DEFAULT_TIMEOUT_MS);
    }

    /**
     * Executes a system command with arguments and a timeout.
     * Prevents memory exhaustion by limiting output size to 16MB.
     */
    private Future<String> execute(List<String> commandWithArgs, long timeoutMs) {
        return vertx.executeBlocking(() -> {
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder(commandWithArgs);
                pb.redirectErrorStream(true); // Merge stdout and stderr
                process = pb.start();

                // Read output with size protection
                String output = readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE);

                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return "Error: Command timed out after " + timeoutMs + "ms";
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    return "Error executing command (exit code " + exitCode + "): " + output;
                }

                return output;
            } catch (SecurityException e) {
                return "Error: Output size exceeded limit of " + (MAX_OUTPUT_SIZE / 1024 / 1024) + "MB";
            } catch (Exception e) {
                return "Error executing command: " + e.getMessage();
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }

    private String readStreamWithLimit(InputStream is, long limit) throws Exception {
        try (BufferedInputStream bis = new BufferedInputStream(is);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            while ((bytesRead = bis.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > limit) {
                    throw new SecurityException("Limit exceeded");
                }
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }
}