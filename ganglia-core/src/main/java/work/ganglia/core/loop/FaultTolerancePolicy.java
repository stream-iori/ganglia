package work.ganglia.core.loop;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.schedule.Schedulable;
import work.ganglia.core.schedule.SchedulableResult;

/**
 * Strategy for handling errors during tool or sub-agent execution.
 */
public interface FaultTolerancePolicy {
    /**
     * Inspects a failed execution and determines if the loop should continue or abort.
     * It may also modify the context (e.g., to record failure counts).
     *
     * @param context The current session context.
     * @param task    The task that was executed.
     * @param result  The result of the execution.
     * @return A Future containing the possibly modified SessionContext if allowed to continue, or a failed Future to abort.
     */
    Future<SessionContext> handleFailure(SessionContext context, Schedulable task, SchedulableResult result);

    /**
     * Called when a task succeeds, allowing the policy to reset any tracked state.
     */
    SessionContext onSuccess(SessionContext context, Schedulable task);
}