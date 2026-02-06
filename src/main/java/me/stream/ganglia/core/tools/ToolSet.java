package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.tools.model.ToolDefinition;
import me.stream.ganglia.core.tools.model.ToolInvokeResult;

import java.util.List;
import java.util.Map;

public interface ToolSet {
    List<ToolDefinition> getDefinitions();
    Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context);
}
