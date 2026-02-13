package me.stream.ganglia.tools.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;

public record ToDoList(List<ToDoItem> items) {
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
        if (items.isEmpty()) return "No tasks.";
        StringBuilder sb = new StringBuilder("ToDo List:\n");
        for (ToDoItem item : items) {
            sb.append(String.format("[%s] %s: %s",
                item.status() == TaskStatus.DONE ? "x" : " ",
                item.id(),
                item.description()));
            if (item.result() != null) {
                sb.append(" -> Result: ").append(item.result());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
