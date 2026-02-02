package me.stream.ganglia.core.model;

import java.util.List;

/**
 * Represents a logical turn in the conversation.
 * A turn typically starts with a user message (or system event) and includes
 * the chain of thoughts, tool calls, and the final assistant response.
 */
public record Turn(
    String id,
    Message userMessage,
    List<Message> intermediateSteps, // Thoughts, ToolCalls, ToolResults
    Message finalResponse
) {
    public Turn withStep(Message step) {
        java.util.ArrayList<Message> newSteps = new java.util.ArrayList<>(intermediateSteps);
        newSteps.add(step);
        return new Turn(id, userMessage, newSteps, finalResponse);
    }

    public Turn withResponse(Message response) {
        return new Turn(id, userMessage, intermediateSteps, response);
    }

    public List<Message> flatten() {
        java.util.ArrayList<Message> list = new java.util.ArrayList<>();
        if (userMessage != null) list.add(userMessage);
        if (intermediateSteps != null) list.addAll(intermediateSteps);
        if (finalResponse != null) list.add(finalResponse);
        return list;
    }
}
