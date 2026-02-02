package me.stream.ganglia.core.prompt;

import me.stream.ganglia.core.memory.KnowledgeBase;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;

public class StandardPromptEngine implements PromptEngine {
    private final KnowledgeBase knowledgeBase;

    public StandardPromptEngine(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public String buildSystemPrompt(SessionContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are Ganglia, an advanced AI software engineer.\n");
        sb.append("You are running in a CLI environment.\n\n");

        // Inject ToDo List
        ToDoList toDoList = context.toDoList();
        if (toDoList != null) {
            sb.append("## Current Plan\n");
            sb.append(toDoList.toString()).append("\n\n");
        } else {
            sb.append("## Current Plan\nNo active plan. You should create one using todo_add if the task is complex.\n\n");
        }

        // Inject Memory Instructions
        sb.append("## Memory & Context\n");
        sb.append("- You have access to a persistent knowledge base (MEMORY.md).\n");
        sb.append("- Use the 'remember' tool to save important facts, user preferences, or architectural decisions.\n");
        sb.append("- Use 'grep' or 'read' to search MEMORY.md if you need to recall project context.\n\n");

        // Inject Guidelines
        sb.append("## Guidelines\n");
        sb.append("- Break down complex tasks into steps using todo_add.\n");
        sb.append("- Mark tasks as complete using todo_complete ONLY when the work is verified.\n");
        sb.append("- Use tools to explore the codebase. Do not hallucinate file contents.\n");
        
        return sb.toString();
    }
}
