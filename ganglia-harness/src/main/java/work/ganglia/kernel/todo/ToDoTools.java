package work.ganglia.kernel.todo;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.memory.ContextCompressor;

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
        new ToolDefinition(
            "todo_add",
            "Add a task to the plan",
            """
                {
                  "type": "object",
                  "properties": {
                    "description": { "type": "string" }
                  },
                  "required": ["description"]
                }
                """),
        new ToolDefinition("todo_list", "List all tasks", "{}"),
        new ToolDefinition(
            "todo_complete",
            "Mark a task as done and compress context",
            """
                {
                  "type": "object",
                  "properties": {
                    "id": { "type": "string" }
                  },
                  "required": ["id"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    return switch (toolName) {
      case "todo_add" -> add(args, context, executionContext);
      case "todo_list" -> list(context);
      case "todo_complete" -> complete(args, context, executionContext);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  @Override
  public Future<ToolInvokeResult> execute(
      ToolCall call,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    return execute(call.toolName(), call.arguments(), context, executionContext);
  }

  public Future<ToolInvokeResult> add(
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    String description = (String) args.get("description");
    ToDoList currentList = getToDoList(context);

    ToDoList newList = currentList.addTask(description);
    SessionContext newContext = context.withNewMetadata("todo_list", newList);

    if (executionContext != null
        && executionContext
            instanceof work.ganglia.port.internal.state.ObservationDispatcher dispatcher) {
      dispatcher.dispatch(
          executionContext.sessionId(),
          work.ganglia.port.external.tool.ObservationType.PLAN_UPDATED,
          "Task added",
          Map.of("plan", newList));
    }

    return Future.succeededFuture(ToolInvokeResult.success("Task added.", newContext));
  }

  private ToDoList getToDoList(SessionContext context) {
    Object obj = context.metadata().get("todo_list");
    if (obj instanceof ToDoList list) {
      return list;
    }
    return ToDoList.empty();
  }

  public Future<ToolInvokeResult> list(SessionContext context) {
    ToDoList list = getToDoList(context);
    return Future.succeededFuture(
        ToolInvokeResult.success(list == null || list.isEmpty() ? "No tasks." : list.toString()));
  }

  public Future<ToolInvokeResult> complete(
      Map<String, Object> args,
      SessionContext context,
      work.ganglia.port.internal.state.ExecutionContext executionContext) {
    String id = (String) args.get("id");
    ToDoList currentList = getToDoList(context);
    if (currentList.isEmpty()) {
      return Future.succeededFuture(ToolInvokeResult.error("No plan exists."));
    }

    // Trigger Compression of Previous Turns
    // Strategy: Summarize all previousTurns, store result in the task, and CLEAR previousTurns.
    List<Turn> turnsToCompress = context.previousTurns();

    return compressor
        .summarize(turnsToCompress, context.modelOptions())
        .map(
            summary -> {
              ToDoList newList =
                  currentList.updateTaskStatus(id, TaskStatus.DONE).updateTaskResult(id, summary);

              if (executionContext != null
                  && executionContext
                      instanceof
                      work.ganglia.port.internal.state.ObservationDispatcher dispatcher) {
                dispatcher.dispatch(
                    executionContext.sessionId(),
                    work.ganglia.port.external.tool.ObservationType.PLAN_UPDATED,
                    "Task completed",
                    Map.of("plan", newList));
              }

              // Clear compressed turns from context
              SessionContext newContext =
                  new SessionContext(
                          context.sessionId(),
                          new ArrayList<>(), // Cleared previous turns
                          context.currentTurn(), // Keep current turn
                          context.metadata(),
                          context.activeSkillIds(),
                          context.modelOptions())
                      .withNewMetadata("todo_list", newList);

              return ToolInvokeResult.success(
                  "Task " + id + " completed. Context compressed: " + summary, newContext);
            })
        .recover(
            err ->
                Future.succeededFuture(
                    ToolInvokeResult.error("Failed to compress context: " + err.getMessage())));
  }
}
