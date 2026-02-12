package me.stream.ganglia.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.model.ToolType;

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
                """,
                ToolType.EXTENSION)
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
