package work.ganglia.core.schedule;

import work.ganglia.core.model.SessionContext;

/**
 * Represents the result of executing a Schedulable task.
 */
public record SchedulableResult(Status status, String output, SessionContext modifiedContext) {

    public enum Status {
        SUCCESS,
        ERROR,
        EXCEPTION,
        INTERRUPT
    }

    public static SchedulableResult success(String output) {
        return new SchedulableResult(Status.SUCCESS, output, null);
    }

    public static SchedulableResult success(String output, SessionContext modifiedContext) {
        return new SchedulableResult(Status.SUCCESS, output, modifiedContext);
    }

    public static SchedulableResult error(String error) {
        return new SchedulableResult(Status.ERROR, error, null);
    }

    public static SchedulableResult interrupt(String message) {
        return new SchedulableResult(Status.INTERRUPT, message, null);
    }
}
