package me.stream.ganglia.core.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.*;

import java.util.List;
import java.util.Map;

public class ToDoTools {
    private final Vertx vertx;

    public ToDoTools(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ToolDefinition> getDefinitions() {
        return List.of(
            new ToolDefinition("todo_add", "Add a task to the plan",
                """
                {
                  "type": "object",
                  "properties": {
                    "description": { "type": "string" }
                  },
                  "required": ["description"]
                }
                """,
                ToolType.BUILTIN),
            new ToolDefinition("todo_list", "List all tasks",
                "{}",
                ToolType.BUILTIN),
            new ToolDefinition("todo_complete", "Mark a task as done",
                """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string" }
                  },
                  "required": ["id"]
                }
                """,
                ToolType.BUILTIN)
        );
    }

    public Future<ToolInvokeResult> add(Map<String, Object> args, SessionContext context) {
        String description = (String) args.get("description");
        ToDoList currentList = context.toDoList();
        if (currentList == null) currentList = ToDoList.empty();
        
        ToDoList newList = currentList.addTask(description);
        SessionContext newContext = context.withToDoList(newList);
        
        return Future.succeededFuture(ToolInvokeResult.success("Task added.", newContext));
    }

    public Future<ToolInvokeResult> list(SessionContext context) {
        ToDoList list = context.toDoList();
        return Future.succeededFuture(ToolInvokeResult.success(list == null ? "No tasks." : list.toString()));
    }

    public Future<ToolInvokeResult> complete(Map<String, Object> args, SessionContext context) {
        String id = (String) args.get("id");
        ToDoList currentList = context.toDoList();
        if (currentList == null) return Future.succeededFuture(ToolInvokeResult.error("No plan exists."));

        try {
            ToDoList newList = currentList.updateTaskStatus(id, TaskStatus.DONE);
            SessionContext newContext = context.withToDoList(newList);
            return Future.succeededFuture(ToolInvokeResult.success("Task " + id + " completed.", newContext));
        } catch (IllegalArgumentException e) {
            return Future.succeededFuture(ToolInvokeResult.error(e.getMessage()));
        }
    }
}
