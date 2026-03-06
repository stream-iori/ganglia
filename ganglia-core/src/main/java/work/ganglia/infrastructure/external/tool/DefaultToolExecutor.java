package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.external.llm.util.ToolCallValidator;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of ToolExecutor that orchestrates standard built-in tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private final List<ToolSet> builtInToolSets = new ArrayList<>();
    private final ToolCallValidator validator = new ToolCallValidator();

    public DefaultToolExecutor(ToolsFactory factory) {
        // Add standard built-in toolsets
        builtInToolSets.add(factory.getBashFileSystemTools());
        builtInToolSets.add(factory.getToDoTools());
        builtInToolSets.add(factory.getKnowledgeBaseTools());
        builtInToolSets.add(factory.getInteractionTools());
        builtInToolSets.add(factory.getWebFetchTools());
        builtInToolSets.add(factory.getBashTools());
        builtInToolSets.add(factory.getFileEditTools());
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context) {
        String toolName = toolCall.toolName();
        log.debug("[TOOL_INVOKE] Name: {}, ID: {}, Args: {}", toolName, toolCall.id(), toolCall.arguments());

        // 1. Find the tool definition for validation
        ToolDefinition definition = findDefinition(toolCall, context);
        if (definition != null) {
            String validationError = validator.validate(toolName, toolCall.arguments(), definition.jsonSchema());
            if (validationError != null) {
                log.warn("[TOOL_VALIDATION_ERROR] Name: {}, Error: {}", toolName, validationError);
                return Future.succeededFuture(ToolInvokeResult.error(validationError));
            }
        }

        // 2. Try built-in tools
        for (ToolSet ts : builtInToolSets) {
            if (hasTool(ts, toolName)) {
                log.debug("Found tool {} in built-in toolset: {}", toolName, ts.getClass().getSimpleName());
                return ts.execute(toolCall, context)
                    .onSuccess(res -> log.debug("[TOOL_RESULT] Name: {}, ID: {}, Status: {}", toolName, toolCall.id(), res.status()))
                    .onFailure(err -> log.error("[TOOL_ERROR] Name: {}, ID: {}, Error: {}", toolName, toolCall.id(), err.getMessage()));
            }
        }

        log.warn("No tool implementation found for: {}", toolName);
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    }

    private ToolDefinition findDefinition(ToolCall call, SessionContext context) {
        return getAvailableTools(context).stream()
            .filter(d -> d.name().equals(call.toolName()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public List<ToolDefinition> getAvailableTools(SessionContext context) {
        List<ToolDefinition> tools = new ArrayList<>();

        for (ToolSet ts : builtInToolSets) {
            tools.addAll(ts.getDefinitions());
        }

        return tools;
    }

    private boolean hasTool(ToolSet ts, String toolName) {
        return ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName));
    }
}
