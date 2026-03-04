package work.ganglia.tools;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToolCall;
import work.ganglia.tools.model.ToolDefinition;
import work.ganglia.tools.model.ToolInvokeResult;
import java.util.List;

public interface ToolExecutor {

    /**
     * Executes a tool call.
     */
    Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context);

    /**
     * Returns the list of available tool definitions.
     */
    List<ToolDefinition> getAvailableTools(SessionContext context);
}
