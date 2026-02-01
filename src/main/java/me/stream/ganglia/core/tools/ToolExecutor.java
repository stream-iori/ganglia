package me.stream.ganglia.core.tools;

import me.stream.ganglia.core.model.ToolCall;
import me.stream.ganglia.core.model.ToolDefinition;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface ToolExecutor {
    
    /**
     * Executes a tool call.
     */
    CompletionStage<String> execute(ToolCall toolCall);
    
    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools();
}
