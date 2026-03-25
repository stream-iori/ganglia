package work.ganglia.kernel.loop;

import io.vertx.core.Future;

import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.chat.SessionContext;

/** Strategy for handling errors during tool or sub-agent execution. */
public interface FaultTolerancePolicy {
  /**
   * Inspects a failed execution and determines if the loop should continue or abort. It may also
   * modify the context (e.g., to record failure counts).
   *
   * @param context The current session context.
   * @param task The task that was executed.
   * @param result The result of the execution.
   * @return A Future containing the possibly modified SessionContext if allowed to continue, or a
   *     failed Future to abort.
   */
  Future<SessionContext> handleFailure(
      SessionContext context, AgentTask task, AgentTaskResult result);

  /** Called when a task succeeds, allowing the policy to reset any tracked state. */
  SessionContext onSuccess(SessionContext context, AgentTask task);
}
