package work.ganglia.kernel.task;

import io.vertx.core.Future;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.ExecutionContext;

public class TaskGraphTask implements AgentTask {
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
  public ToolCall getToolCall() {
    return call;
  }

  @Override
  public Future<AgentTaskResult> execute(
      SessionContext context, ExecutionContext executionContext) {
    // Check recursion
    Object levelObj = context.metadata().getOrDefault("sub_agent_level", 0);
    int currentLevel =
        (levelObj instanceof Number)
            ? ((Number) levelObj).intValue()
            : Integer.parseInt(levelObj.toString());
    if (currentLevel >= 1) {
      return Future.succeededFuture(
          AgentTaskResult.error("RECURSION_LIMIT: Nested task graphs are not allowed."));
    }

    Object approvedObj = call.arguments().getOrDefault("approved", false);
    boolean approved =
        (approvedObj instanceof Boolean)
            ? (Boolean) approvedObj
            : Boolean.parseBoolean(approvedObj.toString());

    if (!approved) {
      return interruptForApproval();
    }

    return executeGraph(context);
  }

  private Future<AgentTaskResult> interruptForApproval() {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) call.arguments().get("nodes");

    StringBuilder sb = new StringBuilder("PROPOSED TASK GRAPH:\n\n");
    for (Map<String, Object> node : nodes) {
      sb.append("- [").append(node.get("id")).append("] ").append(node.get("task"));
      @SuppressWarnings("unchecked")
      List<String> deps = (List<String>) node.get("dependencies");
      if (deps != null && !deps.isEmpty()) {
        sb.append(" (Depends on: ").append(String.join(", ", deps)).append(")");
      }
      sb.append("\n");
    }
    String questionText = sb.toString() + "\nDo you approve this execution plan?";

    Map<String, Object> question = new java.util.HashMap<>();
    question.put("question", questionText);
    question.put("header", "Task Graph");
    question.put("type", "choice");
    question.put(
        "options",
        java.util.List.of(
            java.util.Map.of(
                "label", "Approve", "value", "yes", "description", "Proceed with execution"),
            java.util.Map.of("label", "Reject", "value", "no", "description", "Cancel plan")));

    Map<String, Object> metadata = new java.util.HashMap<>();
    metadata.put("questions", java.util.List.of(question));

    return Future.succeededFuture(AgentTaskResult.interrupt(questionText, metadata));
  }

  private Future<AgentTaskResult> executeGraph(SessionContext context) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> nodeData = (List<Map<String, Object>>) call.arguments().get("nodes");
    List<TaskNode> nodes = new ArrayList<>();

    for (Map<String, Object> data : nodeData) {
      @SuppressWarnings("unchecked")
      List<String> deps = (List<String>) data.getOrDefault("dependencies", Collections.emptyList());

      nodes.add(
          new TaskNode(
              (String) data.get("id"),
              (String) data.get("task"),
              (String) data.getOrDefault("persona", "GENERAL"),
              deps,
              null));
    }

    TaskGraph graph = new TaskGraph(nodes);
    return graphExecutor
        .execute(graph, context)
        .map(AgentTaskResult::success)
        .recover(
            err ->
                Future.succeededFuture(
                    AgentTaskResult.error("GRAPH_EXECUTION_ERROR: " + err.getMessage())));
  }
}
