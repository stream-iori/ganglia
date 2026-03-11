package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

/**
 * A scheduleable task that wraps a standard tool execution (e.g. Bash, FileSystem).
 */
public class StandardToolTask implements Schedulable {
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
    public Future<SchedulableResult> execute(SessionContext context, ExecutionContext executionContext) {
        return toolExecutor.execute(toolCall, context, executionContext)
            .map(invokeResult -> {
                SchedulableResult.Status status = switch (invokeResult.status()) {
                    case SUCCESS -> SchedulableResult.Status.SUCCESS;
                    case ERROR -> SchedulableResult.Status.ERROR;
                    case EXCEPTION -> SchedulableResult.Status.EXCEPTION;
                    case INTERRUPT -> SchedulableResult.Status.INTERRUPT;
                };
                return new SchedulableResult(status, invokeResult.output(), invokeResult.modifiedContext());
            });
    }
}
