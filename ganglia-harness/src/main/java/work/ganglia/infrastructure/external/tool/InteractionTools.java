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
            "Ask the user one or more questions to gather preferences, clarify requirements, or make decisions. When using this tool, prefer providing multiple-choice options with detailed descriptions and enable multi-select where appropriate to provide maximum flexibility.",
            """
{
  "type": "object",
  "required": ["questions"],
  "properties": {
    "questions": {
      "type": "array",
      "description": "A list of questions to ask the user to gather preferences, clarify requirements, or make decisions.",
      "items": {
        "type": "object",
        "required": ["question", "header", "type"],
        "properties": {
          "question": {
            "type": "string",
            "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark."
          },
          "header": {
            "type": "string",
            "description": "Short label (max 16 chars) for the question (e.g., 'Auth', 'Config')."
          },
          "type": {
            "type": "string",
            "enum": ["choice", "text", "yesno"],
            "description": "Question type: 'choice' (multiple-choice), 'text' (free-form), 'yesno' (Yes/No confirmation)."
          },
          "options": {
            "type": "array",
            "description": "Selectable choices for 'choice' type. Provide 2-4 options.",
            "items": {
              "type": "object",
              "required": ["label", "description", "value"],
              "properties": {
                "label": {
                  "type": "string",
                  "description": "Display text for the option (1-5 words)."
                },
                "description": {
                  "type": "string",
                  "description": "Brief explanation of what this option entails."
                },
                "value": {
                  "type": "string",
                  "description": "Unique internal value for this option (e.g., 'option1', 'yes', 'no')."
                }
              }
            }
          },
          "multiSelect": {
            "type": "boolean",
            "description": "If true, allows the user to select multiple options (only for 'choice' type)."
          },
          "placeholder": {
            "type": "string",
            "description": "Hint text shown in the input field (only for 'text' type)."
          }
        }
      }
    }
  }
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
    Object questionsObj = args.get("questions");
    if (!(questionsObj instanceof List<?> questionsList) || questionsList.isEmpty()) {
      return Future.succeededFuture(
          ToolInvokeResult.error("'questions' array is required and cannot be empty."));
    }

    List<Map<String, Object>> questions = new java.util.ArrayList<>();
    StringBuilder textSummary = new StringBuilder();

    for (Object item : questionsList) {
      if (item instanceof Map<?, ?> qMapRaw) {
        @SuppressWarnings("unchecked")
        Map<String, Object> qMap = new java.util.HashMap<>((Map<String, Object>) qMapRaw);
        questions.add(qMap);

        String qText = (String) qMap.get("question");
        String type = (String) qMap.getOrDefault("type", "text");
        textSummary.append("- ").append(qText).append(" (").append(type).append(")\n");

        if ("choice".equals(type)) {
          Object opts = qMap.get("options");
          if (opts instanceof List<?> optList) {
            List<Map<String, Object>> mutableOpts = new java.util.ArrayList<>();
            for (int i = 0; i < optList.size(); i++) {
              Object optItem = optList.get(i);
              if (optItem instanceof Map<?, ?> optMapRaw) {
                @SuppressWarnings("unchecked")
                Map<String, Object> optMap =
                    new java.util.HashMap<>((Map<String, Object>) optMapRaw);

                // Ensure value exists
                if (!optMap.containsKey("value") || optMap.get("value") == null) {
                  String label = String.valueOf(optMap.getOrDefault("label", "option" + i));
                  optMap.put("value", label.toLowerCase().replaceAll("[^a-z0-9]", "_"));
                }

                mutableOpts.add(optMap);

                textSummary.append("  ").append(i + 1).append(". ").append(optMap.get("label"));
                Object desc = optMap.get("description");
                if (desc != null && !desc.toString().isEmpty()) {
                  textSummary.append(" - ").append(desc);
                }
                textSummary.append("\n");
              }
            }
            qMap.put("options", mutableOpts);
          }
        }
      }
    }

    Map<String, Object> data = new java.util.HashMap<>();
    data.put("questions", questions);

    return Future.succeededFuture(ToolInvokeResult.interrupt(textSummary.toString(), data));
  }
}
