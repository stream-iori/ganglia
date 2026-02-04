package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;
import me.stream.ganglia.core.tools.model.ToolType;

import java.util.List;
import java.util.Map;

public class SelectionTools {
    private final Vertx vertx;

    public SelectionTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ask_selection", "Ask the user to select from a list of options",
                """
                {
                  "type": "object",
                  "properties": {
                    "question": { "type": "string", "description": "The question to ask" },
                    "options": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of options"
                    }
                  },
                  "required": ["question", "options"]
                }
                """,
                ToolType.INTERRUPT)
        );
    }

    public Future<ToolInvokeResult> askSelection(Map<String, Object> args, SessionContext context) {
        String question = (String) args.get("question");
        List<String> options = (List<String>) args.get("options");
        
        StringBuilder prompt = new StringBuilder(question).append("\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append(i + 1).append(". ").append(options.get(i)).append("\n");
        }
        prompt.append("Please select an option (number or text):");

        return Future.succeededFuture(ToolInvokeResult.interrupt(prompt.toString()));
    }
}
