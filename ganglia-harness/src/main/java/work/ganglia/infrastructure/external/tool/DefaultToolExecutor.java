package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.infrastructure.external.llm.util.ToolCallValidator;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSet;

/**
 * Default implementation of ToolExecutor that orchestrates standard built-in tool sets and any
 * extra tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
  private static final Logger log = LoggerFactory.getLogger(DefaultToolExecutor.class);

  private final List<ToolSet> toolSets = new ArrayList<>();
  private final ToolCallValidator validator = new ToolCallValidator();

  public DefaultToolExecutor(ToolsFactory factory) {
    this(factory, List.of());
  }

  public DefaultToolExecutor(ToolsFactory factory, List<ToolSet> extraToolSets) {
    // Add extra toolsets (core built-ins and custom ones)
    if (extraToolSets != null) {
      this.toolSets.addAll(extraToolSets);
    }
  }

  @Override
  public Future<ToolInvokeResult> execute(
      ToolCall toolCall,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    String toolName = toolCall.toolName();
    log.debug(
        "[TOOL_INVOKE] Name: {}, ID: {}, Args: {}", toolName, toolCall.id(), toolCall.arguments());

    // 1. Find the tool definition for validation
    ToolDefinition definition = findDefinition(toolCall, context);
    if (definition != null) {
      String validationError =
          validator.validate(toolName, toolCall.arguments(), definition.jsonSchema());
      if (validationError != null) {
        log.warn("[TOOL_VALIDATION_ERROR] Name: {}, Error: {}", toolName, validationError);
        return Future.succeededFuture(ToolInvokeResult.error(validationError));
      }
    }

    // 2. Try tools
    for (ToolSet ts : toolSets) {
      if (hasTool(ts, toolName)) {
        String toolSetName = ts.getClass().getSimpleName();
        if (toolSetName.isEmpty()) {
          toolSetName = ts.getClass().getName();
        }
        log.debug("Found tool {} in toolset: {}", toolName, toolSetName);
        return ts.execute(toolCall, context, executionContext)
            .onSuccess(
                res ->
                    log.debug(
                        "[TOOL_RESULT] Name: {}, ID: {}, Status: {}",
                        toolName,
                        toolCall.id(),
                        res.status()))
            .onFailure(
                err ->
                    log.error(
                        "[TOOL_ERROR] Name: {}, ID: {}, Error: {}",
                        toolName,
                        toolCall.id(),
                        err.getMessage()));
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
    java.util.Map<String, ToolDefinition> tools = new java.util.LinkedHashMap<>();

    for (ToolSet ts : toolSets) {
      for (ToolDefinition d : ts.getDefinitions()) {
        tools.put(d.name(), d);
      }
    }

    return new ArrayList<>(tools.values());
  }

  private boolean hasTool(ToolSet ts, String toolName) {
    return ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolName));
  }
}
