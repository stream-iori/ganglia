package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

/** Represents an executable task that can be scheduled by the ReActAgentLoop. */
public interface AgentTask {
  /**
   * @return The unique identifier of this scheduleable task.
   */
  String id();

  /**
   * @return The name of the task/tool being scheduled.
   */
  String name();

  /**
   * Executes the task.
   *
   * @param context The current session context.
   * @param executionContext The execution context for emitting streams and errors.
   * @return A future containing the result of the execution.
   */
  Future<AgentTaskResult> execute(SessionContext context, ExecutionContext executionContext);

  /**
   * @return The underlying ToolCall that triggered this task, if applicable.
   */
  default ToolCall getToolCall() {
    return null;
  }
}
