package work.ganglia.tools;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;

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
