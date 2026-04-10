package work.ganglia.kernel.todo;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record ToDoList(List<ToDoItem> items) {
  public ToDoList {
    if (items == null) {
      items = java.util.Collections.emptyList();
    }
  }

  public static ToDoList empty() {
    return new ToDoList(new ArrayList<>());
  }

  @JsonIgnore
  public boolean isEmpty() {
    return items == null || items.isEmpty();
  }

  public ToDoList addTask(String description) {
    List<ToDoItem> newItems = new ArrayList<>(items);
    String id = String.valueOf(newItems.size() + 1);
    newItems.add(new ToDoItem(id, description, TaskStatus.TODO, null));
    return new ToDoList(newItems);
  }

  public ToDoList updateTaskStatus(String id, TaskStatus status) {
    List<ToDoItem> newItems = new ArrayList<>();
    boolean found = false;
    for (ToDoItem item : items) {
      if (item.id().equals(id)) {
        newItems.add(item.withStatus(status));
        found = true;
      } else {
        newItems.add(item);
      }
    }
    if (!found) {
      throw new IllegalArgumentException("Task with id " + id + " not found");
    }
    return new ToDoList(newItems);
  }

  public ToDoList updateTaskResult(String id, String result) {
    List<ToDoItem> newItems = new ArrayList<>();
    for (ToDoItem item : items) {
      if (item.id().equals(id)) {
        newItems.add(item.withResult(result));
      } else {
        newItems.add(item);
      }
    }
    return new ToDoList(newItems);
  }

  @Override
  public String toString() {
    if (items.isEmpty()) {
      return "No tasks.";
    }
    StringBuilder sb = new StringBuilder("ToDo List:\n");
    for (ToDoItem item : items) {
      sb.append(
          String.format(
              "[%s] %s: %s",
              item.status() == TaskStatus.DONE ? "x" : " ", item.id(), item.description()));
      if (item.result() != null) {
        sb.append(" -> Result: ").append(item.result());
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  /**
   * Provides a summarized view of the plan by hiding detailed results of completed tasks, while
   * keeping the active (TODO/IN_PROGRESS) tasks fully visible.
   */
  public String toSummarizedString() {
    if (items.isEmpty()) {
      return "No tasks.";
    }
    StringBuilder sb = new StringBuilder("ToDo List (Summarized):\n");
    long doneCount = items.stream().filter(item -> item.status() == TaskStatus.DONE).count();
    long totalCount = items.size();

    if (doneCount > 0) {
      sb.append(
          String.format(
              "... (%d/%d tasks completed, details hidden to save tokens) ...\n",
              doneCount, totalCount));
    }

    for (ToDoItem item : items) {
      if (item.status() != TaskStatus.DONE) {
        sb.append(
            String.format(
                "[%s] %s: %s\n",
                item.status() == TaskStatus.IN_PROGRESS ? "/" : " ",
                item.id(),
                item.description()));
      }
    }
    return sb.toString();
  }
}
