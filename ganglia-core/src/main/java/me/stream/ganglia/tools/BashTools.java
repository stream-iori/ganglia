package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolErrorResult;
import me.stream.ganglia.tools.model.ToolInvokeResult;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BashTools implements ToolSet {
    private static final long MAX_OUTPUT_SIZE = 16 * 1024 * 1024; // 16MB
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60 seconds for general commands

    private final Vertx vertx;

    public BashTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("run_shell_command", "Execute arbitrary bash commands",
                """
                {
                  "type": "object",
                  "properties": {
                    "command": { "type": "string", "description": "The command to execute" }
                  },
                  "required": ["command"]
                }
                """)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("run_shell_command".equals(toolName)) {
            String command = (String) args.get("command");
            return runShellCommand(command);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> runShellCommand(String command) {
        return vertx.<ToolInvokeResult>executeBlocking(() -> {
            Process process = null;
            String partialOutput = "";
            try {
                // Use bash -c to execute arbitrary command strings
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.redirectErrorStream(true);
                process = pb.start();

                StreamResult streamResult = readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE);
                partialOutput = streamResult.content;

                if (streamResult.limitExceeded) {
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit of 16MB", null, partialOutput));
                }

                boolean finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + DEFAULT_TIMEOUT_MS + "ms", null, partialOutput));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    return ToolInvokeResult.error("Command failed with exit code " + exitCode + ": " + partialOutput);
                }

                return ToolInvokeResult.success(partialOutput);
            } catch (Exception e) {
                return ToolInvokeResult.exception(new ToolErrorResult(
                    "run_shell_command", ToolErrorResult.ErrorType.UNKNOWN,
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
