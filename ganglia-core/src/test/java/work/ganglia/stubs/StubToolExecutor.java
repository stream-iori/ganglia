package work.ganglia.stubs;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;

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
