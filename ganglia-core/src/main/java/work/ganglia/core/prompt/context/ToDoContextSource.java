package work.ganglia.core.prompt.context;

import io.vertx.core.Future;
import work.ganglia.core.model.SessionContext;
import work.ganglia.tools.model.ToDoList;

import java.util.List;

public class ToDoContextSource implements ContextSource {
    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        StringBuilder sb = new StringBuilder();
        ToDoList toDoList = sessionContext.toDoList();
        if (toDoList != null && !toDoList.isEmpty()) {
            sb.append(toDoList.toString());
        } else {
            sb.append("No active plan. You should create one using todo_add if the task is complex.");
        }

        sb.append("\n\n- Break down complex tasks into steps using todo_add.\n");
        sb.append("- Mark tasks as complete using todo_complete ONLY when the work is verified.");

        return Future.succeededFuture(List.of(new ContextFragment("Current Plan", sb.toString(), ContextFragment.PRIORITY_PLAN, true)));
    }
}
