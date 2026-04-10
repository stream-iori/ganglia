package work.ganglia.port.external.tool;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;

public interface ToolSet {
  List<ToolDefinition> getDefinitions();

  Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext);

  default Future<ToolInvokeResult> execute(
      ToolCall call, SessionContext context, ExecutionContext executionContext) {
    return execute(call.toolName(), call.arguments(), context, executionContext);
  }

  /**
   * Returns whether this ToolSet's tools should be visible for the given session context. Use this
   * to restrict tool availability based on agent persona, metadata, or other session attributes.
   * Default: always available (backward compatible).
   */
  default boolean isAvailableFor(SessionContext context) {
    return true;
  }
}
