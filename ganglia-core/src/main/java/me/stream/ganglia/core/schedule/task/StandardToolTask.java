package me.stream.ganglia.core.schedule.task;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.schedule.SchedulableResult;
import me.stream.ganglia.core.schedule.Schedulable;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToolCall;

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
    public Future<SchedulableResult> execute(SessionContext context) {
        return toolExecutor.execute(toolCall, context)
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
