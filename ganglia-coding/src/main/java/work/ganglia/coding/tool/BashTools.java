package work.ganglia.coding.tool;

import work.ganglia.util.Constants;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.util.ProcessTracker;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolErrorResult;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.tool.ToolSet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.external.tool.ObservationEvent;
import io.vertx.core.json.JsonObject;

public class BashTools implements ToolSet {
    private static final Logger log = LoggerFactory.getLogger(BashTools.class);
    private static final long MAX_OUTPUT_SIZE = 128 * 1024; // Increased to 128KB for WebUI
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
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context, work.ganglia.port.internal.state.ExecutionContext executionContext) {
        if ("run_shell_command".equals(toolName)) {
            String command = (String) args.get("command");
            // ToolCall ID is usually available in the context of the loop, but for direct ToolExecutor calls
            // we might need a fallback.
            String toolCallId = UUID.randomUUID().toString();

            // Try to extract actual toolCallId if this is invoked via the standard flow
            // This is a bit of a hack since ToolSet.execute doesn't naturally pass the ToolCall object,
            // but the default interface method does. The implementation below assumes the String-based
            // execute is the core one, but the caller (DefaultToolExecutor) uses the ToolCall-based one.
            // Let's rely on the executionContext to provide the ID or we can just pass the execution context.

            return runShellCommand(command, context.sessionId(), toolCallId, executionContext);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> runShellCommand(String command, String sessionId, String toolCallId, work.ganglia.port.internal.state.ExecutionContext executionContext) {
        log.debug("[SHELL_EXEC] Executing: {} (Session: {})", command, sessionId);
        return vertx.<ToolInvokeResult>executeBlocking(() -> {
            Process process = null;
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.redirectErrorStream(true);
                process = pb.start();
                ProcessTracker.track(process);

                final Process p = process;
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final boolean[] limitExceeded = {false};

                Thread readerThread = new Thread(() -> {
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
                                p.destroyForcibly();
                                break;
                            }
                            baos.write(buffer, 0, n);

                            // For TTY streaming, we still need lines or chunks.
                            // Using a simple chunk-based streaming for now.
                            String chunk = new String(buffer, 0, n, StandardCharsets.UTF_8);
                            if (executionContext != null) {
                                executionContext.emitStream(chunk);
                            }
                            }

                    } catch (IOException ignored) {
                    }
                });
                readerThread.setDaemon(true);
                readerThread.start();

                boolean finished = p.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    log.error("[SHELL_TIMEOUT] Command timed out: {}", command);
                    p.destroyForcibly();
                }

                // Wait for reader to catch up and exit
                readerThread.join(1000);
                String partialOutput = baos.toString(StandardCharsets.UTF_8);

                if (limitExceeded[0]) {
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                        "Output size exceeded limit", null, partialOutput));
                }

                if (!finished) {
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + DEFAULT_TIMEOUT_MS + "ms", null, partialOutput));
                }

                int exitCode = p.exitValue();
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
                    "Execution error: " + e.getMessage(), null, ""));
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }
}
