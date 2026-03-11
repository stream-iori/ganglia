package work.ganglia.port.external.tool;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;

import java.util.List;
import java.util.Map;

public interface ToolSet {
    List<ToolDefinition> getDefinitions();

    Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context, ExecutionContext executionContext);

    default Future<ToolInvokeResult> execute(ToolCall call, SessionContext context, ExecutionContext executionContext) {
        return execute(call.toolName(), call.arguments(), context, executionContext);
    }
}
