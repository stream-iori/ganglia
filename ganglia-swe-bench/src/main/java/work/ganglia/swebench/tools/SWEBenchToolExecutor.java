package work.ganglia.swebench.tools;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.swebench.TrajectoryLogger;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.port.internal.state.ExecutionContext;

import java.util.ArrayList;
import java.util.List;

public class SWEBenchToolExecutor implements ToolExecutor {
    private static final Logger log = LoggerFactory.getLogger(SWEBenchToolExecutor.class);
    private final List<ToolSet> toolSets = new ArrayList<>();
    private final TrajectoryLogger trajectoryLogger;

    public SWEBenchToolExecutor(TrajectoryLogger trajectoryLogger) {
        this.trajectoryLogger = trajectoryLogger;
    }

    public void addToolSet(ToolSet toolSet) {
        this.toolSets.add(toolSet);
    }

    @Override
    public Future<ToolInvokeResult> execute(ToolCall toolCall, SessionContext context, ExecutionContext executionContext) {
        for (ToolSet ts : toolSets) {
            if (ts.getDefinitions().stream().anyMatch(d -> d.name().equals(toolCall.toolName()))) {
                return ts.execute(toolCall, context, executionContext)
                    .onSuccess(res -> trajectoryLogger.logToolCall(toolCall.toolName(), toolCall.arguments(), res.output()))
                    .onFailure(err -> trajectoryLogger.logToolCall(toolCall.toolName(), toolCall.arguments(), "ERROR: " + err.getMessage()));
            }
        }
        return Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolCall.toolName()));
    }

    @Override
    public List<ToolDefinition> getAvailableTools(SessionContext context) {
        List<ToolDefinition> defs = new ArrayList<>();
        for (ToolSet ts : toolSets) {
            defs.addAll(ts.getDefinitions());
        }
        return defs;
    }
}
