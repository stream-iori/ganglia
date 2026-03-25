package work.ganglia.kernel.task;

import java.util.List;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;

/** Factory for creating AgentTask tasks from ToolCalls and aggregating capabilities. */
public interface AgentTaskFactory {

  /**
   * Creates a AgentTask task based on the provided ToolCall.
   *
   * @param call The tool call requested by the LLM.
   * @param context The current session context.
   * @return A AgentTask task ready for execution.
   */
  AgentTask create(ToolCall call, SessionContext context);

  /**
   * Returns a consolidated list of all tool definitions available for scheduling.
   *
   * @param context The current session context.
   * @return A list of tool definitions to be provided to the LLM.
   */
  List<ToolDefinition> getAvailableDefinitions(SessionContext context);
}
