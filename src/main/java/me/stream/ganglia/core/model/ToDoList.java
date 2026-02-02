package me.stream.ganglia.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ToDoList(List<ToDoItem> items) {
    public static ToDoList empty() {
        return new ToDoList(new ArrayList<>());
    }

    public ToDoList addTask(String description) {
        List<ToDoItem> newItems = new ArrayList<>(items);
        String id = String.valueOf(newItems.size() + 1);
        newItems.add(new ToDoItem(id, description, TaskStatus.TODO));
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
    
    @Override
    public String toString() {
        if (items.isEmpty()) return "No tasks.";
        StringBuilder sb = new StringBuilder("ToDo List:\n");
        for (ToDoItem item : items) {
            sb.append(String.format("[%s] %s: %s\n", 
                item.status() == TaskStatus.DONE ? "x" : " ", 
                item.id(), 
                item.description()));
        }
        return sb.toString();
    }
}
