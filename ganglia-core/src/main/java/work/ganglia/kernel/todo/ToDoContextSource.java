package work.ganglia.kernel.todo;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

public class ToDoContextSource implements ContextSource {
  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    Object obj = sessionContext.metadata().get("todo_list");
    ToDoList toDoList = obj instanceof ToDoList ? (ToDoList) obj : null;

    if (toDoList == null || toDoList.isEmpty()) {
      String emptyNote =
          "No active plan. You should create one using todo_add if the task is complex.";
      return Future.succeededFuture(
          List.of(ContextFragment.mandatory("Plan Strategy", emptyNote, 12)));
    }

    // 1. Mandatory Core Plan (High priority, summarized, ensures continuity)
    String corePlan =
        toDoList.toSummarizedString()
            + "\n\n- Break down complex tasks into steps using todo_add.\n"
            + "- Mark tasks as complete using todo_complete ONLY when the work is verified.";

    // 2. Detailed Plan (Medium priority, full details, can be pruned to save tokens)
    String detailedPlan = "Full details of all tasks:\n" + toDoList.toString();

    return Future.succeededFuture(
        List.of(
            ContextFragment.mandatory("Active Plan (Summary)", corePlan, 12),
            ContextFragment.prunable(
                "Plan Details (Extended)", detailedPlan, ContextFragment.PRIORITY_PLAN)));
  }
}
