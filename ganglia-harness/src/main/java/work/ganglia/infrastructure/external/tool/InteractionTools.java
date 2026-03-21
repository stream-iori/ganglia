package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import java.util.Map;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;

/** Tools for user interaction, allowing the agent to ask for clarification or selection. */
public class InteractionTools implements ToolSet {
  private final Vertx vertx;

  public InteractionTools(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "ask_selection",
            "Mandatory tool for all user interactions. Use this whenever you need to present choices, ask for clarification, or request confirmation. Using this tool is the ONLY way to trigger the interactive UI buttons/modals for the user.",
            """
                {
                  "type": "object",
                  "properties": {
                    "question": { "type": "string", "description": "The question or prompt to show to the user" },
                    "type": { "type": "string", "enum": ["text", "choice"], "description": "The type of interaction requested. Use 'choice' for clickable buttons." },
                    "options": {
                      "type": "array",
                      "items": { "type": "string" },
                      "description": "List of options for selection (required if type is 'choice')"
                    }
                  },
                  "required": ["question", "type"]
                }
                """,
            true));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    if ("ask_selection".equals(toolName)) {
      return askSelection(args);
    }
    return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
  }

  private Future<ToolInvokeResult> askSelection(Map<String, Object> args) {
    String question = (String) args.get("question");
    String type = (String) args.get("type");

    Map<String, Object> data = new java.util.HashMap<>();
    data.put("question", question);
    data.put("type", type);

    if ("choice".equals(type)) {
      Object optionsObj = args.get("options");
      if (optionsObj == null) {
        return Future.succeededFuture(
            ToolInvokeResult.error("Options are required for 'choice' interaction type"));
      }

      java.util.List<Map<String, String>> structuredOptions = new java.util.ArrayList<>();
      if (optionsObj instanceof java.util.List<?> rawOptions) {
        for (Object opt : rawOptions) {
          if (opt instanceof String s) {
            Map<String, String> m = new java.util.HashMap<>();
            m.put("value", s);
            m.put("label", s);
            m.put("description", "");
            structuredOptions.add(m);
          } else if (opt instanceof Map<?, ?> m) {
            Map<String, String> sm = new java.util.HashMap<>();
            Object v = m.get("value");
            Object l = m.get("label");
            Object d = m.get("description");
            sm.put("value", v != null ? v.toString() : "");
            sm.put("label", l != null ? l.toString() : sm.get("value"));
            sm.put("description", d != null ? d.toString() : "");
            structuredOptions.add(sm);
          }
        }
      }

      if (structuredOptions.isEmpty()) {
        return Future.succeededFuture(
            ToolInvokeResult.error("Options list cannot be empty for 'choice' type"));
      }

      data.put("options", structuredOptions);

      StringBuilder sb = new StringBuilder(question).append("\n\n");
      for (int i = 0; i < structuredOptions.size(); i++) {
        Map<String, String> opt = structuredOptions.get(i);
        sb.append(i + 1).append(". ").append(opt.get("label"));
        if (!opt.get("description").isEmpty()) {
          sb.append(" (").append(opt.get("description")).append(")");
        }
        sb.append("\n");
      }
      sb.append("\nPlease select an option (number or text):");
      return Future.succeededFuture(ToolInvokeResult.interrupt(sb.toString(), data));
    } else {
      // Default to text (free-form input)
      return Future.succeededFuture(ToolInvokeResult.interrupt(question, data));
    }
  }
}
