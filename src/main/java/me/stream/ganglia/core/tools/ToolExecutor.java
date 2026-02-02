package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.tools.model.ToolCall;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;

import java.util.List;

public interface ToolExecutor {

    /**
     * Executes a tool call.
     */
    Future<ToolInvokeResult> execute(ToolCall toolCall, me.stream.ganglia.core.model.SessionContext context);

    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools();
}
