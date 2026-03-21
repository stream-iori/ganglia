package work.ganglia.kernel.todo;

public record ToDoItem(
    String id, String description, TaskStatus status, String result // Summary of the task execution
    ) {
  public ToDoItem(String id, String description, TaskStatus status) {
    this(id, description, status, null);
  }

  public ToDoItem withStatus(TaskStatus newStatus) {
    return new ToDoItem(id, description, newStatus, result);
  }

  public ToDoItem withResult(String newResult) {
    return new ToDoItem(id, description, status, newResult);
  }
}
