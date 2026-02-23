package me.stream.ganglia.stubs;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.ToolExecutor;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class StubToolExecutor implements ToolExecutor {

    private final Map<String, Function<ToolCall, ToolInvokeResult>> toolHandlers = new ConcurrentHashMap<>();
    private final List<ToolDefinition> toolDefinitions;
    private final List<ToolCall> executedCalls = Collections.synchronizedList(new java.util.ArrayList<>());

    public StubToolExecutor(List<ToolDefinition> toolDefinitions) {
        this.toolDefinitions = toolDefinitions;
    }

    public StubToolExecutor() {
        this(Collections.emptyList());
    }

    public void registerHandler(String toolName, Function<ToolCall, ToolInvokeResult> handler) {
        toolHandlers.put(toolName, handler);
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context) {
        executedCalls.add(toolCall);
        Function<ToolCall, ToolInvokeResult> handler = toolHandlers.get(toolCall.toolName());
        if (handler != null) {
            return Future.succeededFuture(handler.apply(toolCall));
        }
        return Future.succeededFuture(ToolInvokeResult.success("Default stub result for " + toolCall.toolName()));
    }

    @Override
    public List<ToolDefinition> getAvailableTools(SessionContext context) {
        return toolDefinitions;
    }

    public List<ToolCall> getExecutedCalls() {
        return executedCalls;
    }
}
