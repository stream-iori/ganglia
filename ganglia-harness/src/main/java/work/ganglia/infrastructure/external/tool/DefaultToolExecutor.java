package work.ganglia.infrastructure.external.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.infrastructure.external.llm.util.ToolCallValidator;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;

/**
 * Default implementation of ToolExecutor that orchestrates standard built-in tool sets and any
 * extra tool sets.
 */
public class DefaultToolExecutor implements ToolExecutor {
  private static final Logger logger = LoggerFactory.getLogger(DefaultToolExecutor.class);

  private final List<ToolSet> toolSets = new ArrayList<>();
  private final ToolCallValidator validator = new ToolCallValidator();
  private final Map<String, ToolSet> toolIndex;

  public DefaultToolExecutor(ToolsFactory factory) {
    this(factory, List.of());
  }

  public DefaultToolExecutor(ToolsFactory factory, List<ToolSet> extraToolSets) {
    // Add extra toolsets (core built-ins and custom ones)
    if (extraToolSets != null) {
      this.toolSets.addAll(extraToolSets);
    }
    this.toolIndex = buildToolIndex();
  }

  private Map<String, ToolSet> buildToolIndex() {
    Map<String, ToolSet> index = new LinkedHashMap<>();
    for (ToolSet ts : toolSets) {
      for (ToolDefinition d : ts.getDefinitions()) {
        index.putIfAbsent(d.name(), ts);
      }
    }
    return index;
  }

  @Override
  public Future<ToolInvokeResult> execute(
      ToolCall toolCall,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    String toolName = toolCall.toolName();
    logger.debug(
        "[TOOL_INVOKE] Name: {}, ID: {}, Args: {}", toolName, toolCall.id(), toolCall.arguments());

    // 1. Find the tool definition for validation
    ToolDefinition definition = findDefinition(toolCall, context);
    if (definition != null) {
      String validationError =
          validator.validate(toolName, toolCall.arguments(), definition.jsonSchema());
      if (validationError != null) {
        logger.warn("[TOOL_VALIDATION_ERROR] Name: {}, Error: {}", toolName, validationError);
        return Future.succeededFuture(ToolInvokeResult.error(validationError));
      }
    }

    // 2. O(1) lookup via tool index, with context-aware availability check
    ToolSet ts = toolIndex.get(toolName);
    if (ts != null && !ts.isAvailableFor(context)) {
      logger.debug("Tool {} not available for current context", toolName);
      return Future.succeededFuture(ToolInvokeResult.error("Tool not available: " + toolName));
    }
    if (ts != null) {
      String toolSetName = ts.getClass().getSimpleName();
      if (toolSetName.isEmpty()) {
        toolSetName = ts.getClass().getName();
      }
      logger.debug("Found tool {} in toolset: {}", toolName, toolSetName);
      return ts.execute(toolCall, context, executionContext)
          .onSuccess(
              res ->
                  logger.debug(
                      "[TOOL_RESULT] Name: {}, ID: {}, Status: {}",
                      toolName,
                      toolCall.id(),
                      res.status()))
          .onFailure(
              err ->
                  logger.error(
                      "[TOOL_ERROR] Name: {}, ID: {}, Error: {}",
                      toolName,
                      toolCall.id(),
                      err.getMessage()));
    }

    logger.warn("No tool implementation found for: {}", toolName);
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
    Map<String, ToolDefinition> tools = new LinkedHashMap<>();

    for (ToolSet ts : toolSets) {
      if (ts.isAvailableFor(context)) {
        for (ToolDefinition d : ts.getDefinitions()) {
          tools.put(d.name(), d);
        }
      }
    }

    return new ArrayList<>(tools.values());
  }
}
