package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.memory.LongTermMemory;

public class KnowledgeBaseTools implements ToolSet {
  private final Vertx vertx;
  private final LongTermMemory longTermMemory;

  public KnowledgeBaseTools(Vertx vertx, LongTermMemory longTermMemory) {
    this.vertx = vertx;
    this.longTermMemory = longTermMemory;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "remember",
            "Save an important fact or preference to long-term memory",
            """
                {
                  "type": "object",
                  "properties": {
                    "fact": { "type": "string", "description": "The fact to remember" }
                  },
                  "required": ["fact"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    if ("remember".equals(toolName)) {
      return remember(args, context);
    }
    return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
  }

  public Future<ToolInvokeResult> remember(Map<String, Object> args, SessionContext context) {
    String fact = (String) args.get("fact");
    return longTermMemory
        .append("- " + fact)
        .map(v -> ToolInvokeResult.success("Remembered: " + fact))
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Failed to remember: " + err.getMessage())));
  }
}
