package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

public class KnowledgeBaseTools implements ToolSet {
    private final Vertx vertx;
    private final KnowledgeBase knowledgeBase;

    public KnowledgeBaseTools(Vertx vertx, KnowledgeBase knowledgeBase) {
        this.vertx = vertx;
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("remember", "Save an important fact or preference to long-term memory",
                """
                {
                  "type": "object",
                  "properties": {
                    "fact": { "type": "string", "description": "The fact to remember" }
                  },
                  "required": ["fact"]
                }
                """)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("remember".equals(toolName)) {
            return remember(args, context);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    public Future<ToolInvokeResult> remember(Map<String, Object> args, SessionContext context) {
        String fact = (String) args.get("fact");
        return knowledgeBase.append("- " + fact)
                .map(v -> ToolInvokeResult.success("Remembered: " + fact))
                .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Failed to remember: " + err.getMessage())));
    }
}
