package work.ganglia.kernel.todo;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.kernel.todo.ToDoList;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.List;

public class ToDoContextSource implements ContextSource {
    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        StringBuilder sb = new StringBuilder();
        Object obj = sessionContext.metadata().get("todo_list");
        ToDoList toDoList = obj instanceof ToDoList ? (ToDoList) obj : null;
        if (toDoList != null && !toDoList.isEmpty()) {
            sb.append(toDoList.toString());
        } else {
            sb.append("No active plan. You should create one using todo_add if the task is complex.");
        }

        sb.append("\n\n- Break down complex tasks into steps using todo_add.\n");
        sb.append("- Mark tasks as complete using todo_complete ONLY when the work is verified.");

        return Future.succeededFuture(List.of(
            ContextFragment.prunable("Current Plan", sb.toString(), ContextFragment.PRIORITY_PLAN)
        ));
    }
}
