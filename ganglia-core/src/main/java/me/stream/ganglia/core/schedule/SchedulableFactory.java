package me.stream.ganglia.core.schedule;

import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;

import java.util.List;

/**
 * Factory for creating Schedulable tasks from ToolCalls and aggregating capabilities.
 */
public interface SchedulableFactory {
    
    /**
     * Creates a Schedulable task based on the provided ToolCall.
     * @param call The tool call requested by the LLM.
     * @param context The current session context.
     * @return A Schedulable task ready for execution.
     */
    Schedulable create(ToolCall call, SessionContext context);

    /**
     * Returns a consolidated list of all tool definitions available for scheduling.
     * @param context The current session context.
     * @return A list of tool definitions to be provided to the LLM.
     */
    List<ToolDefinition> getAvailableDefinitions(SessionContext context);
}
