package me.stream.ganglia.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import java.util.List;

public interface ToolExecutor {

    /**
     * Executes a tool call.
     */
    Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context);

    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools(SessionContext context);
}
