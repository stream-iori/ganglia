package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;

/**
 * Interface for managing daily record persistence.
 */
public interface DailyRecordManager {
    /**
     * Records the goal and accomplishments of a session for a given day.
     *
     * @param sessionId      The ID of the session.
     * @param goal           The session's primary goal.
     * @param accomplishments The summarized results of the session.
     * @return A Future completing when the record is saved.
     */
    Future<Void> record(String sessionId, String goal, String accomplishments);
}
