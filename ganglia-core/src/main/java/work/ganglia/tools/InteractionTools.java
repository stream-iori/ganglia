package work.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

/**
 * Tools for user interaction, allowing the agent to ask for clarification or selection.
 */
public class InteractionTools implements ToolSet {
    private final Vertx vertx;

    public InteractionTools(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("ask_selection", "Ask the user for input or a selection from a list to clarify requirements or resolve ambiguity.",
                """
                {
                  "type": "object",
                  "properties": {
                    "question": { "type": "string", "description": "The question or prompt to show to the user" },
                    "type": { "type": "string", "enum": ["text", "choice"], "description": "The type of interaction requested" },
                    "options": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of options for selection (required if type is 'choice')"
                    }
                  },
                  "required": ["question", "type"]
                }
                """,
                true)
        );
    }

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        if ("ask_selection".equals(toolName)) {
            return askSelection(args);
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private Future<ToolInvokeResult> askSelection(Map<String, Object> args) {
        String question = (String) args.get("question");
        String type = (String) args.get("type");

        if ("choice".equals(type)) {
            List<String> options = (List<String>) args.get("options");
            if (options == null || options.isEmpty()) {
                return Future.succeededFuture(ToolInvokeResult.error("Options are required for 'choice' interaction type"));
            }
            StringBuilder sb = new StringBuilder(question).append("\n\n");
            for (int i = 0; i < options.size(); i++) {
                sb.append(i + 1).append(". ").append(options.get(i)).append("\n");
            }
            sb.append("\nPlease select an option (number or text):");
            return Future.succeededFuture(ToolInvokeResult.interrupt(sb.toString()));
        } else {
            // Default to text (free-form input)
            return Future.succeededFuture(ToolInvokeResult.interrupt(question));
        }
    }
}
