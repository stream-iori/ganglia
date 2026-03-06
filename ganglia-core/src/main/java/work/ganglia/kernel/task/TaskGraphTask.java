package work.ganglia.kernel.task;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TaskGraphTask implements Schedulable {
    private final ToolCall call;
    private final GraphExecutor graphExecutor;

    public TaskGraphTask(ToolCall call, GraphExecutor graphExecutor) {
        this.call = call;
        this.graphExecutor = graphExecutor;
    }

    @Override
    public String id() {
        return call.id();
    }

    @Override
    public String name() {
        return call.toolName();
    }

    @Override
    public Future<SchedulableResult> execute(SessionContext context) {
        // Check recursion
        Object levelObj = context.metadata().getOrDefault("sub_agent_level", 0);
        int currentLevel = (levelObj instanceof Number) ? ((Number) levelObj).intValue() : Integer.parseInt(levelObj.toString());
        if (currentLevel >= 1) {
            return Future.succeededFuture(SchedulableResult.error("RECURSION_LIMIT: Nested task graphs are not allowed."));
        }

        Object approvedObj = call.arguments().getOrDefault("approved", false);
        boolean approved = (approvedObj instanceof Boolean) ? (Boolean) approvedObj : Boolean.parseBoolean(approvedObj.toString());

        if (!approved) {
            return interruptForApproval();
        }

        return executeGraph(context);
    }

    private Future<SchedulableResult> interruptForApproval() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) call.arguments().get("nodes");

        StringBuilder sb = new StringBuilder("PROPOSED TASK GRAPH:\\n\\n");
        for (Map<String, Object> node : nodes) {
            sb.append("- [").append(node.get("id")).append("] ").append(node.get("task"));
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) node.get("dependencies");
            if (deps != null && !deps.isEmpty()) {
                sb.append(" (Depends on: ").append(String.join(", ", deps)).append(")");
            }
            sb.append("\\n");
        }
        sb.append("\\nDo you approve this execution plan? (y/n)");

        return Future.succeededFuture(SchedulableResult.interrupt(sb.toString()));
    }

    private Future<SchedulableResult> executeGraph(SessionContext context) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodeData = (List<Map<String, Object>>) call.arguments().get("nodes");
        List<TaskNode> nodes = new ArrayList<>();

        for (Map<String, Object> data : nodeData) {
            @SuppressWarnings("unchecked")
            List<String> deps = (List<String>) data.getOrDefault("dependencies", Collections.emptyList());

            nodes.add(new TaskNode(
                (String) data.get("id"),
                (String) data.get("task"),
                (String) data.getOrDefault("persona", "GENERAL"),
                deps,
                null
            ));
        }

        TaskGraph graph = new TaskGraph(nodes);
        return graphExecutor.execute(graph, context)
            .map(SchedulableResult::success)
            .recover(err -> Future.succeededFuture(SchedulableResult.error("GRAPH_EXECUTION_ERROR: " + err.getMessage())));
    }
}
