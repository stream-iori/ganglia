package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;
import me.stream.ganglia.core.model.ToolInvokeResult;
import java.util.List;

public interface ToolExecutor {
    
    /**
     * Executes a tool call.
     */
    Future<ToolInvokeResult> execute(ToolCall toolCall);
    
    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools();
}
