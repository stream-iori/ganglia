package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
import me.stream.ganglia.core.tools.model.ToolType;

import java.util.List;
import java.util.Map;

public class KnowledgeBaseTools {
    private final Vertx vertx;
    private final KnowledgeBase knowledgeBase;

    public KnowledgeBaseTools(Vertx vertx, KnowledgeBase knowledgeBase) {
        this.vertx = vertx;
        this.knowledgeBase = knowledgeBase;
    }

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
                """,
                ToolType.BUILTIN)
        );
    }

    public Future<ToolInvokeResult> remember(Map<String, Object> args, SessionContext context) {
        String fact = (String) args.get("fact");
        return knowledgeBase.append("- " + fact)
                .map(v -> ToolInvokeResult.success("Remembered: " + fact))
                .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Failed to remember: " + err.getMessage())));
    }
}
