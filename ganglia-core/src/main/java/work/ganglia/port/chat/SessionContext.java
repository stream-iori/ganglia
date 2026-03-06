package work.ganglia.port.chat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.kernel.todo.ToDoList;
import work.ganglia.port.external.llm.ModelOptions;

import java.util.stream.Collectors;
import java.util.*;
import java.util.stream.Stream;

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
    /**
     * Compact constructor to ensure data integrity and null safety.
     */
    public SessionContext {
        if (sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }
        if (previousTurns == null) {
            previousTurns = Collections.emptyList();
        }
        if (metadata == null) {
            metadata = Collections.emptyMap();
        }
        if (activeSkillIds == null) {
            activeSkillIds = Collections.emptyList();
        }
        if (toDoList == null) {
            toDoList = ToDoList.empty();
        }
    }

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

        Turn newCurrentTurn = Turn.newTurn(UUID.randomUUID().toString(), userMessage);
        return new SessionContext(sessionId, newPreviousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public SessionContext addStep(Message step) {
        Turn newCurrentTurn = currentTurn;
        if (newCurrentTurn == null) {
            newCurrentTurn = new Turn(UUID.randomUUID().toString(), null, new ArrayList<>(), null);
        }
        newCurrentTurn = newCurrentTurn.withStep(step);
        return new SessionContext(sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public SessionContext completeTurn(Message response) {
        Turn newCurrentTurn = currentTurn;
        if (newCurrentTurn == null) {
            newCurrentTurn = new Turn(UUID.randomUUID().toString(), null, new ArrayList<>(), null);
        }
        newCurrentTurn = newCurrentTurn.withResponse(response);
        return new SessionContext(sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    public List<Message> history() {
        return Stream.concat(
            previousTurns.stream().flatMap(t -> t.flatten().stream()),
            currentTurn != null ? currentTurn.flatten().stream() : Stream.empty()
        ).collect(Collectors.toList());
    }

    /**
     * Returns a pruned history (most recent messages) that fits within maxTokens.
     */
    @JsonIgnore
    public List<Message> getPrunedHistory(int maxTokens, TokenCounter counter) {
        List<Message> fullPruned = new ArrayList<>();
        int currentTokens = 0;

        // 1. Always include the current turn (it's the most important)
        if (currentTurn != null) {
            List<Message> currentMessages = currentTurn.flatten();
            for (int i = currentMessages.size() - 1; i >= 0; i--) {
                Message m = currentMessages.get(i);
                currentTokens += m.countTokens(counter);
                fullPruned.add(0, m);
            }
        }

        // 2. Add previous turns as atomic units, moving backwards
        for (int i = previousTurns.size() - 1; i >= 0; i--) {
            Turn turn = previousTurns.get(i);
            List<Message> turnMessages = turn.flatten();
            
            // Calculate tokens for the entire turn
            int turnTokens = 0;
            for (Message m : turnMessages) {
                turnTokens += m.countTokens(counter);
            }

            if (currentTokens + turnTokens > maxTokens && !fullPruned.isEmpty()) {
                break; // Stop if adding this turn exceeds limit
            }

            // Add all messages from this turn to the beginning
            for (int j = turnMessages.size() - 1; j >= 0; j--) {
                fullPruned.add(0, turnMessages.get(j));
            }
            currentTokens += turnTokens;
        }

        return fullPruned;
    }

    public SessionContext withModelOptions(ModelOptions newOptions) {
        return new SessionContext(sessionId, previousTurns, currentTurn, metadata, activeSkillIds, newOptions, toDoList);
    }

    public SessionContext withToDoList(ToDoList newToDoList) {
        return new SessionContext(sessionId, previousTurns, currentTurn, metadata, activeSkillIds, modelOptions, newToDoList);
    }

    public SessionContext withPreviousTurns(List<Turn> newPreviousTurns) {
        return new SessionContext(sessionId, newPreviousTurns, currentTurn, metadata, activeSkillIds, modelOptions, toDoList);
    }

    /**
     * Returns the iteration count for the current turn.
     */
    @JsonIgnore
    public int getIterationCount() {
        return currentTurn != null ? currentTurn.getIterationCount() : 0;
    }
}
