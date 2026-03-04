package work.ganglia.tools;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToolCall;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

public interface ToolSet {
    List<ToolDefinition> getDefinitions();

    Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context);

    default Future<ToolInvokeResult> execute(ToolCall call, SessionContext context) {
        return execute(call.toolName(), call.arguments(), context);
    }
}
