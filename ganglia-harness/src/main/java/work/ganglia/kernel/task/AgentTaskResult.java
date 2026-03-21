package work.ganglia.kernel.task;

import work.ganglia.port.chat.SessionContext;

/** Represents the result of executing a AgentTask task. */
public record AgentTaskResult(Status status, String output, SessionContext modifiedContext) {

  public enum Status {
    SUCCESS,
    ERROR,
    EXCEPTION,
    INTERRUPT
  }

  public static AgentTaskResult success(String output) {
    return new AgentTaskResult(Status.SUCCESS, output, null);
  }

  public static AgentTaskResult success(String output, SessionContext modifiedContext) {
    return new AgentTaskResult(Status.SUCCESS, output, modifiedContext);
  }

  public static AgentTaskResult error(String error) {
    return new AgentTaskResult(Status.ERROR, error, null);
  }

  public static AgentTaskResult interrupt(String message) {
    return new AgentTaskResult(Status.INTERRUPT, message, null);
  }
}
