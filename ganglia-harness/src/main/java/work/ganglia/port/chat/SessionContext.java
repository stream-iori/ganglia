package work.ganglia.port.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;

import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.util.TokenCounter;

/** Represents the full context of a running session, organized by Turns. */
public record SessionContext(
    String sessionId,
    List<Turn> previousTurns,
    Turn currentTurn,
    Map<String, Object> metadata,
    List<String> activeSkillIds,
    ModelOptions modelOptions,
    CompressionState compressionState) {
  /** Compact constructor to ensure data integrity and null safety. */
  public SessionContext {
    if (sessionId == null) {
      sessionId = UUID.randomUUID().toString();
    }
    previousTurns = previousTurns == null ? Collections.emptyList() : List.copyOf(previousTurns);
    metadata = metadata == null ? Collections.emptyMap() : Map.copyOf(metadata);
    activeSkillIds = activeSkillIds == null ? Collections.emptyList() : List.copyOf(activeSkillIds);
    compressionState = compressionState == null ? CompressionState.empty() : compressionState;
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
    return new SessionContext(
        sessionId, newPreviousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, compressionState);
  }

  public SessionContext addStep(Message step) {
    Turn newCurrentTurn = currentTurn;
    if (newCurrentTurn == null) {
      newCurrentTurn = Turn.newTurn(UUID.randomUUID().toString(), null);
    }
    newCurrentTurn = newCurrentTurn.withStep(step);
    return new SessionContext(
        sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, compressionState);
  }

  public SessionContext completeTurn(Message response) {
    Turn newCurrentTurn = currentTurn;
    if (newCurrentTurn == null) {
      newCurrentTurn = Turn.newTurn(UUID.randomUUID().toString(), null);
    }
    newCurrentTurn = newCurrentTurn.withResponse(response);
    return new SessionContext(
        sessionId, previousTurns, newCurrentTurn, metadata, activeSkillIds, modelOptions, compressionState);
  }

  public List<Message> history() {
    return Stream.concat(
            previousTurns.stream().flatMap(t -> t.flatten().stream()),
            currentTurn != null ? currentTurn.flatten().stream() : Stream.empty())
        .collect(Collectors.toList());
  }

  /** Returns a pruned history (most recent messages) that fits within maxTokens. */
  @JsonIgnore
  public List<Message> getPrunedHistory(int maxTokens, TokenCounter counter) {
    return getPrunedHistory(maxTokens, Integer.MAX_VALUE, counter);
  }

  /**
   * Returns a pruned history that fits within maxTokens, with the current turn capped at
   * currentTurnBudget. The userMessage of the current turn is always preserved; intermediate steps
   * are included from newest to oldest using the same skip pattern as previous turns.
   */
  @JsonIgnore
  public List<Message> getPrunedHistory(
      int maxTokens, int currentTurnBudget, TokenCounter counter) {
    List<Message> fullPruned = new ArrayList<>();
    int currentTokens = 0;

    // 1. Include the current turn with per-turn budget cap.
    //    userMessage is always preserved; intermediate steps are pruned newest-first
    //    using the same skip pattern applied to previous turns.
    if (currentTurn != null) {
      // 1a. Always include userMessage
      if (currentTurn.userMessage() != null) {
        int userMsgTokens = currentTurn.userMessage().countTokens(counter);
        currentTokens += userMsgTokens;
        fullPruned.add(currentTurn.userMessage());
      }

      // 1b. Always include finalResponse (most recent output)
      int reservedForFinal = 0;
      if (currentTurn.finalResponse() != null) {
        reservedForFinal = currentTurn.finalResponse().countTokens(counter);
      }

      // 1c. Add intermediate steps from newest to oldest, respecting currentTurnBudget.
      //     Same reverse-iterate + skip pattern as previousTurns below.
      List<Message> steps = currentTurn.intermediateSteps();
      List<Message> keptSteps = new ArrayList<>();
      int stepTokens = currentTokens + reservedForFinal;
      for (int i = steps.size() - 1; i >= 0; i--) {
        Message m = steps.get(i);
        int msgTokens = m.countTokens(counter);
        if (stepTokens + msgTokens > currentTurnBudget) {
          continue; // skip oversized / over-budget step, keep searching older ones
        }
        stepTokens += msgTokens;
        keptSteps.add(0, m);
      }
      fullPruned.addAll(keptSteps);
      currentTokens = stepTokens;

      // 1d. Append finalResponse after steps
      if (currentTurn.finalResponse() != null) {
        fullPruned.add(currentTurn.finalResponse());
      }
    }

    // 2. Add previous turns as atomic units, moving backwards.
    // Use continue (not break) so that one oversized turn does not discard all older turns.
    for (int i = previousTurns.size() - 1; i >= 0; i--) {
      Turn turn = previousTurns.get(i);
      List<Message> turnMessages = turn.flatten();

      // Calculate tokens for the entire turn
      int turnTokens = 0;
      for (Message m : turnMessages) {
        turnTokens += m.countTokens(counter);
      }

      if (currentTokens + turnTokens > maxTokens) {
        continue; // Skip this oversized turn but keep searching older turns
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
    return new SessionContext(
        sessionId, previousTurns, currentTurn, metadata, activeSkillIds, newOptions, compressionState);
  }

  public SessionContext withMetadata(Map<String, Object> newMetadata) {
    return new SessionContext(
        sessionId, previousTurns, currentTurn, newMetadata, activeSkillIds, modelOptions, compressionState);
  }

  public SessionContext withNewMetadata(String key, Object value) {
    Map<String, Object> newMetadata = new HashMap<>(metadata);
    newMetadata.put(key, value);
    return withMetadata(Collections.unmodifiableMap(newMetadata));
  }

  /** Returns a new context with updated compression state. */
  public SessionContext withCompressionState(CompressionState newState) {
    return new SessionContext(
        sessionId, previousTurns, currentTurn, metadata, activeSkillIds, modelOptions, newState);
  }

  // ── Compression State Convenience Methods ───────────────────────────────

  /** Returns the running summary from compression state. */
  @JsonIgnore
  public String getRunningSummary() {
    return compressionState.runningSummary();
  }

  /** Returns a new context with the running summary updated. */
  public SessionContext withRunningSummary(String summary) {
    return withCompressionState(compressionState.withRunningSummary(summary));
  }

  /** Returns the number of consecutive compression failures. */
  @JsonIgnore
  public int getConsecutiveCompressionFailures() {
    return compressionState.consecutiveFailures();
  }

  /** Returns a new context with the compression failure counter incremented. */
  public SessionContext withCompressionFailure() {
    return withCompressionState(compressionState.withFailure());
  }

  /** Returns a new context with the compression failure counter reset to 0. */
  public SessionContext resetCompressionFailures() {
    return withCompressionState(compressionState.resetFailures());
  }

  // ── Last Assistant Timestamp (for time-based microcompact) ─────────────

  /** Returns the timestamp of the last assistant message, for time-based microcompact. */
  @JsonIgnore
  public Instant getLastAssistantTimestamp() {
    // First check compression state
    if (compressionState.lastAssistantTimestamp() != null) {
      return compressionState.lastAssistantTimestamp();
    }

    // Fall back to scanning turns
    if (currentTurn != null) {
      Instant ts = currentTurn.getLastAssistantTimestamp();
      if (ts != null) {
        return ts;
      }
    }

    for (int i = previousTurns.size() - 1; i >= 0; i--) {
      Instant ts = previousTurns.get(i).getLastAssistantTimestamp();
      if (ts != null) {
        return ts;
      }
    }

    return null;
  }

  /** Returns a new context with the last assistant timestamp updated. */
  public SessionContext withLastAssistantTimestamp(Instant timestamp) {
    return withCompressionState(compressionState.withLastAssistantTimestamp(timestamp));
  }

  // ── Compact Boundary Navigation ─────────────────────────────────────────

  /** Finds the most recent compact boundary turn. */
  @JsonIgnore
  public Optional<Turn> findLastCompactBoundary() {
    for (int i = previousTurns.size() - 1; i >= 0; i--) {
      Turn turn = previousTurns.get(i);
      if (turn.isCompactBoundary()) {
        return Optional.of(turn);
      }
    }
    return Optional.empty();
  }

  /** Returns all turns after the last compact boundary. */
  @JsonIgnore
  public List<Turn> getTurnsAfterLastBoundary() {
    int boundaryIndex = -1;
    for (int i = previousTurns.size() - 1; i >= 0; i--) {
      if (previousTurns.get(i).isCompactBoundary()) {
        boundaryIndex = i;
        break;
      }
    }
    return new ArrayList<>(previousTurns.subList(boundaryIndex + 1, previousTurns.size()));
  }

  // ── Running Summary Validation (for session memory compact) ─────────────

  /** Checks if the running summary is valid and non-trivial. */
  @JsonIgnore
  public boolean hasValidRunningSummary() {
    return compressionState.hasValidRunningSummary();
  }

  public SessionContext withPreviousTurns(List<Turn> newPreviousTurns) {
    return new SessionContext(
        sessionId, newPreviousTurns, currentTurn, metadata, activeSkillIds, modelOptions, compressionState);
  }

  /** Returns the iteration count for the current turn. */
  @JsonIgnore
  public int getIterationCount() {
    return currentTurn != null ? currentTurn.getIterationCount() : 0;
  }

  /** Convenience method to persist this context using the provided manager. */
  public io.vertx.core.Future<SessionContext> persistWith(
      work.ganglia.port.internal.state.SessionManager manager) {
    return manager.persist(this).map(v -> this);
  }
}
