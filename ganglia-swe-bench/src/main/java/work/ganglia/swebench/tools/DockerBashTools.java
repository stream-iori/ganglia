package work.ganglia.swebench.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.core.model.SessionContext;
import work.ganglia.swebench.SandboxManager;
import work.ganglia.tools.ToolSet;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DockerBashTools implements ToolSet {
    private static final Logger log = LoggerFactory.getLogger(DockerBashTools.class);
    private final Vertx vertx;
    private final SandboxManager sandbox;

    public DockerBashTools(Vertx vertx, SandboxManager sandbox) {
        this.vertx = vertx;
        this.sandbox = sandbox;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("run_shell_command", "Execute arbitrary bash commands in the docker sandbox",
                """
                {
                  "type": "object",
                  "properties": {
                    "command": {
                      "type": "string",
                      "description": "The command to execute"
                    }
                  },
                  "required": ["command"]
                }
                """
            )
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
        log.debug("[DOCKER_EXEC] Executing: {}", command);
        return vertx.executeBlocking(() -> {
            try {
                // Execute directly using the SandboxManager's exec capabilities
                String output = sandbox.exec(command);
                return ToolInvokeResult.success(output);
            } catch (Exception e) {
                log.error("[DOCKER_EXEC_ERROR] Failed: {}", command, e);
                return ToolInvokeResult.error("Execution error: " + e.getMessage());
            }
        });
    }
}
