package work.ganglia.kernel.loop;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.chat.SessionContext;

/** Aborts the loop if consecutive tool failures reach a specified threshold. */
public class ConsecutiveFailurePolicy implements FaultTolerancePolicy {
  private static final Logger logger = LoggerFactory.getLogger(ConsecutiveFailurePolicy.class);

  private final int maxConsecutiveFailures;

  public ConsecutiveFailurePolicy(int maxConsecutiveFailures) {
    this.maxConsecutiveFailures = maxConsecutiveFailures;
  }

  public ConsecutiveFailurePolicy() {
    this(3); // Default to 3
  }

  @Override
  public Future<SessionContext> handleFailure(
      SessionContext context, AgentTask task, AgentTaskResult result) {
    if (result.status() == AgentTaskResult.Status.ERROR
        || result.status() == AgentTaskResult.Status.EXCEPTION) {
      int fails = (int) context.metadata().getOrDefault("consecutive_tool_failures", 0);
      if (fails >= maxConsecutiveFailures - 1) { // 0-based, so checking against 2 for max 3
        logger.warn(
            "Task {} failed {} times consecutively. Aborting loop to prevent runaway usage.",
            task.name(),
            fails + 1);
        return Future.failedFuture("Aborting due to repetitive task failures: " + result.output());
      }
      Map<String, Object> newMetadata = new HashMap<>(context.metadata());
      newMetadata.put("consecutive_tool_failures", fails + 1);
      return Future.succeededFuture(context.withMetadata(newMetadata));
    }
    return Future.succeededFuture(context);
  }

  @Override
  public SessionContext onSuccess(SessionContext context, AgentTask task) {
    if (context.metadata().containsKey("consecutive_tool_failures")) {
      Map<String, Object> newMetadata = new HashMap<>(context.metadata());
      newMetadata.remove("consecutive_tool_failures");
      return context.withMetadata(newMetadata);
    }
    return context;
  }
}
