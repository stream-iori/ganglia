package work.ganglia.swebench.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.swebench.SandboxManager;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class DockerFileSystemTools implements ToolSet {
    private static final Logger log = LoggerFactory.getLogger(DockerFileSystemTools.class);
    private final Vertx vertx;
    private final SandboxManager sandbox;

    public DockerFileSystemTools(Vertx vertx, SandboxManager sandbox) {
        this.vertx = vertx;
        this.sandbox = sandbox;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("list_directory", "List files in a directory inside Docker",
                "{ \"type\": \"object\", \"properties\": { \"path\": { \"type\": \"string\" } }, \"required\": [\"path\"] }"),
            new ToolDefinition("read_file", "Read content of a file inside Docker",
                "{ \"type\": \"object\", \"properties\": { \"path\": { \"type\": \"string\" } }, \"required\": [\"path\"] }"),
            new ToolDefinition("grep_search", "Search for a pattern in files inside Docker",
                "{ \"type\": \"object\", \"properties\": { \"path\": { \"type\": \"string\" }, \"pattern\": { \"type\": \"string\" } }, \"required\": [\"path\", \"pattern\"] }")
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context, work.ganglia.port.internal.state.ExecutionContext executionContext) {
        return vertx.executeBlocking(() -> {
            try {
                String path = (String) args.getOrDefault("path", "/workspace");
                String output = "";

                switch (toolName) {
                    case "list_directory":
                        output = sandbox.exec("ls", "-la", path);
                        break;
                    case "read_file":
                        output = sandbox.exec("cat", path);
                        break;
                    case "grep_search":
                        String pattern = (String) args.get("pattern");
                        output = sandbox.exec("grep", "-rnE", pattern, path);
                        break;
                    default:
                        return ToolInvokeResult.error("Unknown tool: " + toolName);
                }
                return ToolInvokeResult.success(output);
            } catch (Exception e) {
                log.error("Docker FS tool execution failed", e);
                return ToolInvokeResult.error("FS Error: " + e.getMessage());
            }
        });
    }
}
