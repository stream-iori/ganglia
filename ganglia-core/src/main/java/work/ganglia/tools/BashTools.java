package work.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.util.ProcessTracker;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolErrorResult;
import work.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import work.ganglia.core.webui.model.TtyEvent;
import io.vertx.core.json.JsonObject;
import java.io.InputStreamReader;
import java.io.BufferedReader;

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
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("run_shell_command".equals(toolName)) {
            String command = (String) args.get("command");
            return runShellCommand(command, context.sessionId(), context);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> runShellCommand(String command, String sessionId, SessionContext context) {
        log.debug("[SHELL_EXEC] Executing: {} (Session: {})", command, sessionId);
        return vertx.<ToolInvokeResult>executeBlocking(() -> {
            Process process = null;
            StringBuilder outputBuilder = new StringBuilder();
            try {
                ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
                pb.redirectErrorStream(true);
                process = pb.start();
                ProcessTracker.track(process);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // For future: add signal check back if we add it to SessionContext record
                        
                        outputBuilder.append(line).append("\n");
                        
                        // Publish to EventBus bypass TTY topic
                        TtyEvent ttyEvent = new TtyEvent("", line + "\n", false);
                        vertx.eventBus().publish("ganglia.ui.stream." + sessionId + ".tty", JsonObject.mapFrom(ttyEvent));

                        if (outputBuilder.length() > MAX_OUTPUT_SIZE) {
                            log.warn("[SHELL_LIMIT] Output size exceeded for: {}", command);
                            process.destroyForcibly();
                            return ToolInvokeResult.exception(new ToolErrorResult(
                                "run_shell_command", ToolErrorResult.ErrorType.SIZE_LIMIT_EXCEEDED,
                                "Output size exceeded limit", null, outputBuilder.toString()));
                        }
                    }
                }

                boolean finished = process.waitFor(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!finished) {
                    log.error("[SHELL_TIMEOUT] Command timed out: {}", command);
                    process.destroyForcibly();
                    return ToolInvokeResult.exception(new ToolErrorResult(
                        "run_shell_command", ToolErrorResult.ErrorType.TIMEOUT,
                        "Command timed out after " + DEFAULT_TIMEOUT_MS + "ms", null, outputBuilder.toString()));
                }

                int exitCode = process.exitValue();
                if (exitCode != 0) {
                    log.debug("[SHELL_FAIL] Exit code: {}, Command: {}", exitCode, command);
                    return ToolInvokeResult.error("Command failed with exit code " + exitCode + ": " + outputBuilder.toString());
                }

                log.debug("[SHELL_SUCCESS] Command: {}", command);
                return ToolInvokeResult.success(outputBuilder.toString());
            } catch (Exception e) {
                log.error("[SHELL_ERROR] Exception for: {}", command, e);
                return ToolInvokeResult.exception(new ToolErrorResult(
                    "run_shell_command", ToolErrorResult.ErrorType.UNKNOWN,
                    "Execution error: " + e.getMessage(), null, outputBuilder.toString()));
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroyForcibly();
                }
            }
        });
    }
}
