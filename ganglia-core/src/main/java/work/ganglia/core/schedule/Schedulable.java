package work.ganglia.core.schedule;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;

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
     * @return A future containing the result of the execution.
     */
    Future<SchedulableResult> execute(SessionContext context);
}
