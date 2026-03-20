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
                "{ \"type\": \"object\", \"properties\": { \"path\": { \"type\": \"string\" }, \"pattern\": { \"type\": \"string\" }, \"include\": { \"type\": \"string\" } }, \"required\": [\"path\", \"pattern\"] }"),
            new ToolDefinition("glob", "Find files matching a pattern inside Docker",
                "{ \"type\": \"object\", \"properties\": { \"path\": { \"type\": \"string\" }, \"pattern\": { \"type\": \"string\" } }, \"required\": [\"path\", \"pattern\"] }"),
            new ToolDefinition("read_files", "Read multiple files at once inside Docker",
                "{ \"type\": \"object\", \"properties\": { \"paths\": { \"type\": \"array\", \"items\": { \"type\": \"string\" } }, \"limit_per_file\": { \"type\": \"integer\", \"default\": 300 } }, \"required\": [\"paths\"] }")
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
                        String include = (String) args.get("include");
                        String grepCmd = "grep -rnE " +
                            "--exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target --exclude-dir=venv --exclude-dir=.venv --exclude-dir=__pycache__ ";
                        if (include != null && !include.isEmpty()) {
                            grepCmd += "--include=" + include + " ";
                        }
                        output = sandbox.exec(grepCmd + "'" + pattern.replace("'", "'\\''") + "' " + path);
                        break;
                    case "glob":
                        String globPattern = (String) args.get("pattern");
                        String findPattern = globPattern.replace("**/", "");
                        String findCmd = "find " + path +
                            " -type d \\( -name .git -o -name node_modules -o -name __pycache__ -o -name target -o -name venv -o -name .venv \\) -prune" +
                            " -o -type f -name '" + findPattern.replace("'", "'\\''") + "' -print";
                        output = sandbox.exec(findCmd);
                        break;
                    case "read_files":
                        List<String> paths = (List<String>) args.get("paths");
                        if (paths == null || paths.isEmpty()) return ToolInvokeResult.error("No paths provided");
                        int limitPerFile = ((Number) args.getOrDefault("limit_per_file", 300)).intValue();
                        String escapedPaths = paths.stream()
                            .map(p -> "'" + p.replace("'", "'\\''") + "'")
                            .collect(java.util.stream.Collectors.joining(" "));
                        String script = String.format("limit=%d; files=(%s); for f in \"${files[@]}\"; do echo \"--- FILE: $f ---\"; if [ -f \"$f\" ]; then head -n \"$limit\" \"$f\"; total=$(wc -l < \"$f\" | tr -d ' '); if [ \"$total\" -gt \"$limit\" ]; then echo \"\"; echo \"--- [TRUNCATED: $limit of $total lines shown. Use 'read_file' for full content.] ---\"; fi; else echo \"[ERROR: File not found]\"; fi; echo \"\"; done", limitPerFile, escapedPaths);
                        output = sandbox.exec(script);
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
