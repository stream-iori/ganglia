package work.ganglia.infrastructure.external.tool;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemorySecurityScanner;

public class KnowledgeBaseTools implements ToolSet {
  private final Vertx vertx;
  private final LongTermMemory longTermMemory;
  private final MemorySecurityScanner securityScanner = new MemorySecurityScanner();

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
                    "fact": { "type": "string", "description": "The fact to remember" },
                    "target": {
                      "type": "string",
                      "enum": ["project", "user"],
                      "description": "Where to store: 'project' for project knowledge (default), 'user' for user preferences and profile",
                      "default": "project"
                    }
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
    String target = (String) args.getOrDefault("target", "project");

    // Security scan before persisting
    MemorySecurityScanner.ScanResult scanResult = securityScanner.scan(fact);
    if (!scanResult.isSafe()) {
      return Future.succeededFuture(
          ToolInvokeResult.error(
              "Memory blocked by security scan: " + String.join("; ", scanResult.threats())));
    }

    String topic =
        "user".equals(target) ? LongTermMemory.USER_PROFILE_TOPIC : LongTermMemory.DEFAULT_TOPIC;
    return longTermMemory
        .append(topic, "- " + fact + "\n")
        .map(v -> ToolInvokeResult.success("Remembered in " + target + " memory: " + fact))
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Failed to remember: " + err.getMessage())));
  }
}
