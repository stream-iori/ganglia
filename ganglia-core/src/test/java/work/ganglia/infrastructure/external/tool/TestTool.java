package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

public class TestTool implements ToolSet {
    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("test_tool", "A tool for testing skills",
                """
                {
                  "type": "object",
                  "properties": {
                    "arg": { "type": "string" }
                  }
                }
                """)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("test_tool".equals(toolName)) {
            return Future.succeededFuture(ToolInvokeResult.success("Test tool executed with arg: " + args.get("arg")));
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }
}
