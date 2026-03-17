package work.ganglia.infrastructure.mcp;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.mcp.McpCallToolRequest;
import work.ganglia.port.mcp.McpClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class McpToolSet implements ToolSet {

    private final McpClient client;
    private final List<ToolDefinition> definitions;

    private McpToolSet(McpClient client, List<ToolDefinition> definitions) {
        this.client = client;
        this.definitions = definitions;
    }

    /**
     * Creates an McpToolSet by fetching the tools from the MCP Server.
     */
    public static Future<McpToolSet> create(McpClient client) {
        return client.listTools().map(result -> {
            List<ToolDefinition> defs = result.tools().stream()
                .map(t -> new ToolDefinition(
                    t.name(),
                    t.description(),
                    new JsonObject(t.inputSchema()).encode() // convert schema Map to JSON string
                ))
                .collect(Collectors.toList());
            return new McpToolSet(client, defs);
        });
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return definitions;
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context, ExecutionContext executionContext) {
        McpCallToolRequest request = new McpCallToolRequest(toolName, args);
        return client.callTool(request).map(result -> {
            if (Boolean.TRUE.equals(result.isError())) {
                String errorMsg = result.content().stream()
                    .map(c -> c.text() != null ? c.text() : "")
                    .collect(Collectors.joining("\n"));
                return ToolInvokeResult.error(errorMsg);
            } else {
                String output = result.content().stream()
                    .map(c -> c.text() != null ? c.text() : (c.data() != null ? "[Image Data]" : ""))
                    .collect(Collectors.joining("\n"));
                return ToolInvokeResult.success(output);
            }
        }).recover(err -> Future.succeededFuture(ToolInvokeResult.error("MCP Tool invocation failed: " + err.getMessage())));
    }
}
