package work.ganglia.infrastructure.internal.state;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.internal.prompt.MicrocompactConfig;

/**
 * Time-based microcompact clears old tool results when the gap since the last assistant message
 * exceeds a threshold.
 *
 * <p>This optimization is based on the observation that LLM providers have a prompt cache TTL
 * (typically ~60 minutes). After this gap, the cache is cold anyway, so clearing old tool results
 * doesn't lose any caching benefit but saves tokens.
 *
 * <p>Only specific "compactable" tool types are cleared: read_file, bash, grep, glob, web_fetch,
 * web_search. These tools produce large outputs that are often no longer relevant after a time gap.
 */
public class TimeBasedMicrocompact {
  private static final Logger logger = LoggerFactory.getLogger(TimeBasedMicrocompact.class);

  /** Tool types that can be compacted (produce large outputs, often stale after time gap). */
  private static final Set<String> COMPACTABLE_TOOLS =
      Set.of(
          "read_file",
          "read",
          "bash",
          "grep",
          "glob",
          "web_fetch",
          "web_search",
          "webfetch",
          "websearch");

  /** Placeholder message for cleared tool results. */
  private static final String CLEARED_MESSAGE = "[Old tool result cleared]";

  /**
   * Performs time-based microcompact if conditions are met.
   *
   * @param context the session context
   * @param config the microcompact configuration
   * @return the context with old tool results cleared, or the original context if not triggered
   */
  public SessionContext compactIfNeeded(SessionContext context, MicrocompactConfig config) {
    if (!config.enabled()) {
      return context;
    }

    // Find the last assistant message timestamp
    Instant lastAssistant = findLastAssistantTimestamp(context);
    if (lastAssistant == null) {
      return context;
    }

    // Check if gap exceeds threshold
    long gapMinutes = Duration.between(lastAssistant, Instant.now()).toMinutes();
    if (gapMinutes < config.gapThresholdMinutes()) {
      return context;
    }

    logger.info(
        "Time-based microcompact triggered: {} minutes since last assistant message (threshold: {})",
        gapMinutes,
        config.gapThresholdMinutes());

    return clearOldToolResults(context, config.keepRecent());
  }

  /**
   * Finds the timestamp of the most recent assistant message in the context.
   *
   * @param context the session context
   * @return the timestamp of the last assistant message, or null if none found
   */
  private Instant findLastAssistantTimestamp(SessionContext context) {
    // Check current turn first
    if (context.currentTurn() != null) {
      Instant ts = findLastAssistantInTurn(context.currentTurn());
      if (ts != null) {
        return ts;
      }
    }

    // Check previous turns in reverse order
    List<Turn> turns = context.previousTurns();
    for (int i = turns.size() - 1; i >= 0; i--) {
      Instant ts = findLastAssistantInTurn(turns.get(i));
      if (ts != null) {
        return ts;
      }
    }

    return null;
  }

  /**
   * Finds the timestamp of the most recent assistant message in a turn.
   *
   * @param turn the turn to search
   * @return the timestamp of the last assistant message, or null if none
   */
  private Instant findLastAssistantInTurn(Turn turn) {
    // Check final response first
    if (turn.finalResponse() != null
        && turn.finalResponse().role() == Role.ASSISTANT
        && turn.finalResponse().timestamp() != null) {
      return turn.finalResponse().timestamp();
    }

    // Check intermediate steps in reverse
    List<Message> steps = turn.intermediateSteps();
    for (int i = steps.size() - 1; i >= 0; i--) {
      Message msg = steps.get(i);
      if (msg.role() == Role.ASSISTANT && msg.timestamp() != null) {
        return msg.timestamp();
      }
    }

    return null;
  }

  /**
   * Clears old tool results from the context, keeping the most recent N.
   *
   * @param context the session context
   * @param keepRecent number of most recent tool results to preserve
   * @return the context with old tool results cleared
   */
  private SessionContext clearOldToolResults(SessionContext context, int keepRecent) {
    // Collect all compactable tool result locations
    List<ToolResultLocation> allResults = collectCompactableToolResults(context);

    if (allResults.size() <= keepRecent) {
      return context;
    }

    // Sort by timestamp (most recent first) and identify which to clear
    allResults.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

    Set<String> idsToClear = new HashSet<>();
    for (int i = keepRecent; i < allResults.size(); i++) {
      idsToClear.add(allResults.get(i).toolCallId);
    }

    if (idsToClear.isEmpty()) {
      return context;
    }

    logger.info(
        "Clearing {} old tool results (keeping {} most recent)", idsToClear.size(), keepRecent);

    // Clear the identified tool results
    return clearToolResultsByIds(context, idsToClear);
  }

  /** Collects all compactable tool results from the context. */
  private List<ToolResultLocation> collectCompactableToolResults(SessionContext context) {
    List<ToolResultLocation> results = new ArrayList<>();

    // Process previous turns
    for (Turn turn : context.previousTurns()) {
      collectFromTurn(turn, results);
    }

    // Process current turn
    if (context.currentTurn() != null) {
      collectFromTurn(context.currentTurn(), results);
    }

    return results;
  }

  private void collectFromTurn(Turn turn, List<ToolResultLocation> results) {
    for (Message msg : turn.intermediateSteps()) {
      if (msg.role() == Role.TOOL
          && msg.toolObservation() != null
          && isCompactableTool(msg.toolObservation().toolName())) {
        results.add(
            new ToolResultLocation(
                msg.toolObservation().toolCallId(),
                msg.toolObservation().toolName(),
                msg.timestamp() != null ? msg.timestamp().toEpochMilli() : turn.timestamp()));
      }
    }
  }

  private boolean isCompactableTool(String toolName) {
    if (toolName == null) return false;
    String lower = toolName.toLowerCase();
    return COMPACTABLE_TOOLS.contains(lower);
  }

  /** Clears tool results matching the given IDs. */
  private SessionContext clearToolResultsByIds(SessionContext context, Set<String> idsToClear) {
    // Process previous turns
    List<Turn> turns = context.previousTurns();
    List<Turn> newTurns = new ArrayList<>();

    for (Turn turn : turns) {
      newTurns.add(clearToolResultsInTurn(turn, idsToClear));
    }

    // Process current turn
    Turn newCurrentTurn = context.currentTurn();
    if (newCurrentTurn != null) {
      newCurrentTurn = clearToolResultsInTurn(newCurrentTurn, idsToClear);
    }

    return new SessionContext(
        context.sessionId(),
        newTurns,
        newCurrentTurn,
        context.metadata(),
        context.activeSkillIds(),
        context.modelOptions());
  }

  private Turn clearToolResultsInTurn(Turn turn, Set<String> idsToClear) {
    List<Message> newSteps = new ArrayList<>();
    boolean anyChanged = false;

    for (Message msg : turn.intermediateSteps()) {
      if (msg.role() == Role.TOOL
          && msg.toolObservation() != null
          && idsToClear.contains(msg.toolObservation().toolCallId())) {
        // Replace with cleared message
        msg =
            Message.tool(
                msg.toolObservation().toolCallId(),
                msg.toolObservation().toolName(),
                CLEARED_MESSAGE);
        anyChanged = true;
      }
      newSteps.add(msg);
    }

    if (!anyChanged) {
      return turn;
    }

    return turn.withIntermediateSteps(newSteps);
  }

  /** Record to track tool result locations. */
  private record ToolResultLocation(String toolCallId, String toolName, long timestamp) {}
}
