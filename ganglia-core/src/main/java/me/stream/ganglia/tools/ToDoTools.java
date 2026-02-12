package me.stream.ganglia.tools;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.core.model.*;
import me.stream.ganglia.tools.model.*;
import me.stream.ganglia.tools.model.ToolDefinition;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.model.ToolType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ToDoTools implements ToolSet {
    private final Vertx vertx;
    private final ContextCompressor compressor;

    public ToDoTools(Vertx vertx, ContextCompressor compressor) {
        this.vertx = vertx;
        this.compressor = compressor;
    }

    @Override
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
            new ToolDefinition("todo_complete", "Mark a task as done and compress context",
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

    @Override
    public Future<ToolInvokeResult> execute(String toolName, Map<String, Object> args, SessionContext context) {
        return switch (toolName) {
            case "todo_add" -> add(args, context);
            case "todo_list" -> list(context);
            case "todo_complete" -> complete(args, context);
            default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
        };
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

        // Trigger Compression of Previous Turns
        // Strategy: Summarize all previousTurns, store result in the task, and CLEAR previousTurns.
        List<Turn> turnsToCompress = context.previousTurns();

        return compressor.summarize(turnsToCompress, context.modelOptions())
                .map(summary -> {
                    ToDoList newList = currentList.updateTaskStatus(id, TaskStatus.DONE)
                                                  .updateTaskResult(id, summary);

                    // Clear compressed turns from context
                    SessionContext newContext = new SessionContext(
                            context.sessionId(),
                            new ArrayList<>(), // Cleared previous turns
                            context.currentTurn(), // Keep current turn
                            context.metadata(),
                            context.activeSkillIds(),
                            context.modelOptions(),
                            newList
                    );

                    return ToolInvokeResult.success("Task " + id + " completed. Context compressed: " + summary, newContext);
                })
                .recover(err -> Future.succeededFuture(ToolInvokeResult.error("Failed to compress context: " + err.getMessage())));
    }
}
