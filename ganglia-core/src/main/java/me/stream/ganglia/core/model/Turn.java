package me.stream.ganglia.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import me.stream.ganglia.tools.model.ToolCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Turn {
        if (intermediateSteps == null) {
            intermediateSteps = Collections.emptyList();
        }
    }

    public static Turn newTurn(String id, Message msg) {
        return new Turn(id, msg, new ArrayList<>(), null);
    }

    public Turn withStep(Message step) {
        ArrayList<Message> newSteps = new ArrayList<>(intermediateSteps);
        newSteps.add(step);
        return new Turn(id, userMessage, newSteps, finalResponse);
    }

    public Turn withResponse(Message response) {
        return new Turn(id, userMessage, intermediateSteps, response);
    }

    public List<Message> flatten() {
        ArrayList<Message> list = new ArrayList<>();
        if (Objects.nonNull(userMessage)) list.add(userMessage);
        list.addAll(intermediateSteps);
        if (Objects.nonNull(finalResponse)) list.add(finalResponse);
        return list;
    }

    /**
     * Returns the most recent message in this turn.
     */
    @JsonIgnore
    public Message getLatestMessage() {
        if (finalResponse != null) return finalResponse;
        if (intermediateSteps != null && !intermediateSteps.isEmpty()) {
            return intermediateSteps.get(intermediateSteps.size() - 1);
        }
        return userMessage;
    }

    /**
     * Finds tool calls from the last assistant message in this turn that are still unanswered.
     */
    @JsonIgnore
    public List<ToolCall> getPendingToolCalls() {
        if (intermediateSteps == null || intermediateSteps.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. Find the last assistant message that had tool calls
        Message lastAssistant = intermediateSteps.stream()
            .filter(m -> m.role() == Role.ASSISTANT && m.toolCalls() != null && !m.toolCalls().isEmpty())
            .reduce((first, second) -> second) // Get the last one
            .orElse(null);

        if (lastAssistant == null) {
            return Collections.emptyList();
        }

        // 2. Identify answered tool call IDs
        Set<String> answeredIds = intermediateSteps.stream()
            .filter(m -> m.role() == Role.TOOL && m.toolObservation() != null)
            .map(m -> m.toolObservation().toolCallId())
            .collect(Collectors.toSet());

        // 3. Filter for those that remain unanswered
        return lastAssistant.toolCalls().stream()
            .filter(tc -> !answeredIds.contains(tc.id()))
            .toList();
    }
}
