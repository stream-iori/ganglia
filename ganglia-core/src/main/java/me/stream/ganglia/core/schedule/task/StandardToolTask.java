package me.stream.ganglia.core.schedule.task;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.schedule.ScheduleResult;
import me.stream.ganglia.core.schedule.Scheduleable;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToolCall;

/**
 * A scheduleable task that wraps a standard tool execution (e.g. Bash, FileSystem).
 */
public class StandardToolTask implements Scheduleable {
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
    public Future<ScheduleResult> execute(SessionContext context) {
        return toolExecutor.execute(toolCall, context)
            .map(invokeResult -> {
                ScheduleResult.Status status = switch (invokeResult.status()) {
                    case SUCCESS -> ScheduleResult.Status.SUCCESS;
                    case ERROR -> ScheduleResult.Status.ERROR;
                    case EXCEPTION -> ScheduleResult.Status.EXCEPTION;
                    case INTERRUPT -> ScheduleResult.Status.INTERRUPT;
                };
                return new ScheduleResult(status, invokeResult.output(), invokeResult.modifiedContext());
            });
    }
}
