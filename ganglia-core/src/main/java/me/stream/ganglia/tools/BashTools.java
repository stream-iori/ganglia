package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.util.ProcessTracker;
import me.stream.ganglia.tools.model.ToolCall;
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

public class BashTools implements ToolSet {
    private static final Logger log = LoggerFactory.getLogger(BashTools.class);
    private static final long MAX_OUTPUT_SIZE = 8 * 1024; // 8KB
    private static final long DEFAULT_TIMEOUT_MS = 60000; // 60 seconds for general commands

    private final Vertx vertx;

    public BashTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("run_shell_command", "Execute arbitrary bash commands",
                "{\n  \"type\": \"object\",\n  \"properties\": {\n    \"command\": {\n      \"type\": \"string\",\n      \"description\": \"The command to execute\"\n    }\n  },\n  \"required\": [\"command\"]\n}")
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
        log.debug("[SHELL_EXEC] Executing: {}", command);
        return vertx.<ToolInvokeResult>executeBlocking(() -> {
            Process process = null;
            String partialOutput = "";
            try {
                // Use bash -c to execute arbitrary command strings
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.redirectErrorStream(true);
                process = pb.start();
                ProcessTracker.track(process);

                StreamResult streamResult = readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE);
                partialOutput = streamResult.content;

                if (streamResult.limitExceeded) {
                    log.warn("[SHELL_LIMIT] Output size exceeded for: {}", command);
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit of 8KB", null, partialOutput));
                }

                boolean finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    log.error("[SHELL_TIMEOUT] Command timed out: {}", command);
                    process.destroyForcibly();
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + DEFAULT_TIMEOUT_MS + "ms", null, partialOutput));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.debug("[SHELL_FAIL] Exit code: {}, Command: {}", exitCode, command);
                    return ToolInvokeResult.error("Command failed with exit code " + exitCode + ": " + partialOutput);
                }

                log.debug("[SHELL_SUCCESS] Command: {}", command);
                return ToolInvokeResult.success(partialOutput);
            } catch (Exception e) {
                log.error("[SHELL_ERROR] Exception for: {}", command, e);
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
