package me.stream.ganglia.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

public interface ToolSet {
    List<ToolDefinition> getDefinitions();
    Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context);
}
