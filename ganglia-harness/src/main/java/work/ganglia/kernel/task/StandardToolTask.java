package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.internal.state.ExecutionContext;

/** A scheduleable task that wraps a standard tool execution (e.g. Bash, FileSystem). */
public class StandardToolTask implements AgentTask {
  private final ToolCall toolCall;
  private final ToolExecutor toolExecutor;

  public StandardToolTask(ToolCall toolCall, ToolExecutor toolExecutor) {
    this.toolCall = toolCall;
    this.toolExecutor = toolExecutor;
  }

  @Override
  public String id() {
    return toolCall.id();
  }

  @Override
  public String name() {
    return toolCall.toolName();
  }

  @Override
  public ToolCall getToolCall() {
    return toolCall;
  }

  @Override
  public Future<AgentTaskResult> execute(
      SessionContext context, ExecutionContext executionContext) {
    return toolExecutor
        .execute(toolCall, context, executionContext)
        .map(
            invokeResult -> {
              AgentTaskResult.Status status =
                  switch (invokeResult.status()) {
                    case SUCCESS -> AgentTaskResult.Status.SUCCESS;
                    case ERROR -> AgentTaskResult.Status.ERROR;
                    case EXCEPTION -> AgentTaskResult.Status.EXCEPTION;
                    case INTERRUPT -> AgentTaskResult.Status.INTERRUPT;
                  };
              return new AgentTaskResult(
                  status, invokeResult.output(), invokeResult.modifiedContext());
            });
  }
}
