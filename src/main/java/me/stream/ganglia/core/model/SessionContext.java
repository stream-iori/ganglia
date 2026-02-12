package me.stream.ganglia.core.model;

import me.stream.ganglia.tools.model.ToDoList;

import java.util.ArrayList;
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
        if (msg.role() == Role.USER) {
            return startTurn(msg);
        } else {
            return addStep(msg);
        }
    }

    public SessionContext startTurn(Message userMessage) {
        List<Turn> newPreviousTurns = new ArrayList<>(previousTurns);
        if (currentTurn != null) {
            newPreviousTurns.add(currentTurn);
        }
        Turn newCurrentTurn = new Turn(java.util.UUID.randomUUID().toString(), userMessage, new ArrayList<>(), null);
        return new SessionContext(sessionId, newPreviousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public SessionContext addStep(Message step) {
        Turn newCurrentTurn = currentTurn;
        if (newCurrentTurn == null) {
            newCurrentTurn = new Turn(java.util.UUID.randomUUID().toString(), null, new ArrayList<>(), null);
        }
        newCurrentTurn = newCurrentTurn.withStep(step);
        return new SessionContext(sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public SessionContext completeTurn(Message response) {
        Turn newCurrentTurn = currentTurn;
        if (newCurrentTurn == null) {
            newCurrentTurn = new Turn(java.util.UUID.randomUUID().toString(), null, new ArrayList<>(), null);
        }
        newCurrentTurn = newCurrentTurn.withResponse(response);
        return new SessionContext(sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
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
