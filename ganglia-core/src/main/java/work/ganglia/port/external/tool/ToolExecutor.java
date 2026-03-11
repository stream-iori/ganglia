package work.ganglia.port.external.tool;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;

import java.util.List;

public interface ToolExecutor {

    /**
     * Executes a tool call.
     */
    Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context, ExecutionContext executionContext);

    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools(SessionContext context);
}
