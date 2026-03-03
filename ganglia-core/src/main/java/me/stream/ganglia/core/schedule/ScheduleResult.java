package me.stream.ganglia.core.schedule;

import me.stream.ganglia.core.model.SessionContext;

/**
 * Represents the result of executing a Scheduleable task.
 */
public record ScheduleResult(Status status, String output, SessionContext modifiedContext) {
    
    public enum Status { 
        SUCCESS, 
        ERROR, 
        EXCEPTION, 
        INTERRUPT 
    }

    public static ScheduleResult success(String output) {
        return new ScheduleResult(Status.SUCCESS, output, null);
    }

    public static ScheduleResult success(String output, SessionContext modifiedContext) {
        return new ScheduleResult(Status.SUCCESS, output, modifiedContext);
    }

    public static ScheduleResult error(String error) {
        return new ScheduleResult(Status.ERROR, error, null);
    }

    public static ScheduleResult interrupt(String message) {
        return new ScheduleResult(Status.INTERRUPT, message, null);
    }
}
