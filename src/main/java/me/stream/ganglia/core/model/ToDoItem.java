package me.stream.ganglia.core.model;

public record ToDoItem(
    String id,
    String description,
    TaskStatus status
) {
    public ToDoItem withStatus(TaskStatus newStatus) {
        return new ToDoItem(id, description, newStatus);
    }
}
