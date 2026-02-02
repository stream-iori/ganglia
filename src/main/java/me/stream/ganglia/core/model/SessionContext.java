package me.stream.ganglia.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents the full context of a running session, organized by Turns.
 */
public record SessionContext(
    String sessionId,
    List<Turn> previousTurns,
    Turn currentTurn,
    Map<String, Object> metadata,
    List<String> activeSkillIds,
    ModelOptions modelOptions,
    ToDoList toDoList
) {
    public SessionContext withNewMessage(Message msg) {
        List<Turn> newPreviousTurns = new ArrayList<>(previousTurns);
        Turn newCurrentTurn = currentTurn;

        if (msg.role() == Role.USER) {
            // New User message implies a NEW turn.
            if (newCurrentTurn != null) {
                newPreviousTurns.add(newCurrentTurn);
            }
            newCurrentTurn = new Turn(java.util.UUID.randomUUID().toString(), msg, new ArrayList<>(), null);
        } else {
            // Assistant or Tool message belongs to current turn
            if (newCurrentTurn == null) {
                // Should not happen if flow is correct, but safe guard
                newCurrentTurn = new Turn(java.util.UUID.randomUUID().toString(), null, new ArrayList<>(), null);
            }
            
            // Heuristic: If it has content and no tool calls, it *might* be final response?
            // ReAct logic: Final Answer is an Assistant message with content (and no tool calls).
            // But we treat it as just a message for now.
            // Let's assume non-User messages are "steps" unless we explicitly mark them as final response?
            // For now, let's just append to intermediateSteps if it's not the final answer.
            // Or simplifies: just use withStep for everything except the last one?
            // Let's implement generic logic:
            newCurrentTurn = newCurrentTurn.withStep(msg);
        }

        return new SessionContext(sessionId, newPreviousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public List<Message> history() {
        List<Message> list = new ArrayList<>();
        for (Turn t : previousTurns) {
            list.addAll(t.flatten());
        }
        if (currentTurn != null) {
            list.addAll(currentTurn.flatten());
        }
        return list;
    }

    public SessionContext withModelOptions(ModelOptions newOptions) {
        return new SessionContext(sessionId, previousTurns, currentTurn, metadata, activeSkillIds, newOptions, toDoList);
    }

    public SessionContext withToDoList(ToDoList newToDoList) {
        return new SessionContext(sessionId, previousTurns, currentTurn, metadata, activeSkillIds, modelOptions, newToDoList);
    }
}