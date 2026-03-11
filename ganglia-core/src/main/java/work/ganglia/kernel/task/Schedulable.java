package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.ExecutionContext;

/**
 * Represents an executable task that can be scheduled by the StandardAgentLoop.
 */
public interface Schedulable {
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
     * @param context The current session context.
     * @param executionContext The execution context for emitting streams and errors.
     * @return A future containing the result of the execution.
     */
    Future<SchedulableResult> execute(SessionContext context, ExecutionContext executionContext);
}
