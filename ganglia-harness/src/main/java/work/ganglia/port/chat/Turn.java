package work.ganglia.port.chat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import work.ganglia.port.external.tool.ToolCall;

/**
 * Represents a logical turn in the conversation. A turn typically starts with a user message (or
 * system event) and includes the chain of thoughts, tool calls, and the final assistant response.
 */
public record Turn(
    String id,
    Message userMessage,
    List<Message> intermediateSteps, // Thoughts, ToolCalls, ToolResults
    Message finalResponse,
    long timestamp) { // Epoch millis when turn was created
  public Turn {
    intermediateSteps =
        intermediateSteps == null ? Collections.emptyList() : List.copyOf(intermediateSteps);
  }

  public static Turn newTurn(String id, Message msg) {
    return new Turn(id, msg, new ArrayList<>(), null, System.currentTimeMillis());
  }

  public Turn withStep(Message step) {
    ArrayList<Message> newSteps = new ArrayList<>(intermediateSteps);
    newSteps.add(step);
    return new Turn(id, userMessage, newSteps, finalResponse, timestamp);
  }

  public Turn withResponse(Message response) {
    return new Turn(id, userMessage, intermediateSteps, response, timestamp);
  }

  public Turn withIntermediateSteps(List<Message> steps) {
    return new Turn(id, userMessage, steps, finalResponse, timestamp);
  }

  public List<Message> flatten() {
    ArrayList<Message> list = new ArrayList<>();
    if (Objects.nonNull(userMessage)) {
      list.add(userMessage);
    }
    list.addAll(intermediateSteps);
    if (Objects.nonNull(finalResponse)) {
      list.add(finalResponse);
    }
    return list;
  }

  /** Returns the most recent message in this turn. */
  @JsonIgnore
  public Message getLatestMessage() {
    if (finalResponse != null) {
      return finalResponse;
    }
    if (intermediateSteps != null && !intermediateSteps.isEmpty()) {
      return intermediateSteps.get(intermediateSteps.size() - 1);
    }
    return userMessage;
  }

  /** Finds tool calls from the last assistant message in this turn that are still unanswered. */
  @JsonIgnore
  public List<ToolCall> getPendingToolCalls() {
    if (intermediateSteps == null || intermediateSteps.isEmpty()) {
      return Collections.emptyList();
    }

    // 1. Find the last assistant message that had tool calls
    Message lastAssistant =
        intermediateSteps.stream()
            .filter(
                m ->
                    m.role() == Role.ASSISTANT && m.toolCalls() != null && !m.toolCalls().isEmpty())
            .reduce((first, second) -> second) // Get the last one
            .orElse(null);

    if (lastAssistant == null) {
      return Collections.emptyList();
    }

    // 2. Identify answered tool call IDs
    Set<String> answeredIds =
        intermediateSteps.stream()
            .filter(m -> m.role() == Role.TOOL && m.toolObservation() != null)
            .filter(
                m ->
                    m.content() != null
                        && !m.content().startsWith("INTERRUPTED:")) // Exclude placeholders
            .map(m -> m.toolObservation().toolCallId())
            .collect(Collectors.toSet());

    // 3. Filter for those that remain unanswered
    return lastAssistant.toolCalls().stream().filter(tc -> !answeredIds.contains(tc.id())).toList();
  }

  /**
   * Calculates how many iterations of reasoning-acting have been performed in this turn. Each
   * iteration corresponds to an assistant message in the intermediate steps.
   */
  @JsonIgnore
  public int getIterationCount() {
    if (intermediateSteps == null || intermediateSteps.isEmpty()) {
      return 0;
    }
    return (int) intermediateSteps.stream().filter(m -> m.role() == Role.ASSISTANT).count();
  }

  /**
   * Checks if this turn is a compact boundary marker.
   *
   * @return true if this turn marks a compression boundary
   */
  @JsonIgnore
  public boolean isCompactBoundary() {
    return CompactBoundaryTurn.isCompactBoundaryId(id);
  }

  /**
   * Checks if this turn has meaningful text content (for session memory compact eligibility).
   *
   * @return true if this turn contains text content
   */
  @JsonIgnore
  public boolean hasTextContent() {
    if (userMessage != null && userMessage.content() != null && !userMessage.content().isBlank()) {
      return true;
    }
    if (finalResponse != null
        && finalResponse.content() != null
        && !finalResponse.content().isBlank()) {
      return true;
    }
    return intermediateSteps.stream().anyMatch(m -> m.content() != null && !m.content().isBlank());
  }

  /**
   * Gets the timestamp of the most recent assistant message in this turn.
   *
   * @return the timestamp of the last assistant message, or null if none
   */
  @JsonIgnore
  public Instant getLastAssistantTimestamp() {
    // Check final response first
    if (finalResponse != null
        && finalResponse.role() == Role.ASSISTANT
        && finalResponse.timestamp() != null) {
      return finalResponse.timestamp();
    }

    // Check intermediate steps in reverse
    for (int i = intermediateSteps.size() - 1; i >= 0; i--) {
      Message msg = intermediateSteps.get(i);
      if (msg.role() == Role.ASSISTANT && msg.timestamp() != null) {
        return msg.timestamp();
      }
    }

    return null;
  }

  /**
   * Estimates the total token count for this turn.
   *
   * @param counter the token counter to use
   * @return estimated token count
   */
  public int estimateTokens(work.ganglia.util.TokenCounter counter) {
    return flatten().stream().mapToInt(m -> m.countTokens(counter)).sum();
  }
}
