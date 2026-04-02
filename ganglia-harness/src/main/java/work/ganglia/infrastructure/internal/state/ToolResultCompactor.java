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

/**
 * Unified tool result compaction supporting multiple strategies.
 *
 * <p>This class consolidates the previously separate implementations:
 *
 * <ul>
 *   <li>Time-based microcompact - clears tool results when gap since last assistant exceeds threshold
 *   <li>Cache TTL-based compact - clears tool results older than cache TTL
 * </ul>
 *
 * <p>Both strategies serve the same purpose: clear old tool results to save tokens when they are
 * unlikely to benefit from prompt caching.
 */
public class ToolResultCompactor {
  private static final Logger logger = LoggerFactory.getLogger(ToolResultCompactor.class);

  /** Default tools that can be compacted (produce large outputs, often stale after time gap). */
  public static final Set<String> DEFAULT_COMPACTABLE_TOOLS =
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
   * Compacts tool results based on time gap since last assistant message.
   *
   * <p>This strategy is based on the observation that LLM providers have a prompt cache TTL
   * (typically ~60 minutes). After this gap, the cache is cold anyway, so clearing old tool
   * results doesn't lose any caching benefit but saves tokens.
   *
   * @param context the session context
   * @param gapThresholdMinutes minimum minutes since last assistant message to trigger compact
   * @param keepRecent number of most recent tool results to preserve
   * @param toolFilter set of tool names to compact (null = all compactable tools)
   * @return the context with old tool results cleared, or the original context if not triggered
   */
  public SessionContext compactByTimeGap(
      SessionContext context,
      long gapThresholdMinutes,
      int keepRecent,
      Set<String> toolFilter) {

    // Find the last assistant message timestamp
    Instant lastAssistant = findLastAssistantTimestamp(context);
    if (lastAssistant == null) {
      return context;
    }

    // Check if gap exceeds threshold
    long gapMinutes = Duration.between(lastAssistant, Instant.now()).toMinutes();
    if (gapMinutes < gapThresholdMinutes) {
      return context;
    }

    logger.info(
        "Time-gap compact triggered: {} minutes since last assistant message (threshold: {})",
        gapMinutes,
        gapThresholdMinutes);

    return compactByAge(context, keepRecent, toolFilter);
  }

  /**
   * Compacts tool results based on cache TTL (absolute age).
   *
   * <p>This strategy clears tool results older than the specified TTL, regardless of when the last
   * assistant message was sent. Useful when the prompt cache TTL is known.
   *
   * @param context the session context
   * @param cacheTtlMs maximum age in milliseconds before clearing tool results
   * @param keepRecent number of most recent tool results to preserve
   * @param toolFilter set of tool names to compact (null = all tools)
   * @return the context with old tool results cleared, or the original context if not triggered
   */
  public SessionContext compactByCacheTtl(
      SessionContext context,
      long cacheTtlMs,
      int keepRecent,
      Set<String> toolFilter) {

    long now = System.currentTimeMillis();
    long cutoffTime = now - cacheTtlMs;

    // Collect all tool result locations
    List<ToolResultLocation> allResults = collectToolResults(context, toolFilter);

    if (allResults.isEmpty()) {
      return context;
    }

    // Sort by timestamp (most recent first) and identify which to clear
    allResults.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

    Set<String> idsToClear = new HashSet<>();
    for (int i = keepRecent; i < allResults.size(); i++) {
      ToolResultLocation loc = allResults.get(i);
      // Also check if the tool result is older than TTL
      if (loc.timestamp < cutoffTime) {
        idsToClear.add(loc.toolCallId);
      }
    }

    if (idsToClear.isEmpty()) {
      return context;
    }

    logger.info(
        "Cache-TTL compact: clearing {} old tool results (keeping {} most recent, TTL: {} ms)",
        idsToClear.size(),
        keepRecent,
        cacheTtlMs);

    return clearToolResultsByIds(context, idsToClear);
  }

  /**
   * Compacts by age, keeping only the most recent N tool results.
   *
   * @param context the session context
   * @param keepRecent number of most recent tool results to preserve
   * @param toolFilter set of tool names to compact (null = all compactable tools)
   * @return the context with old tool results cleared
   */
  private SessionContext compactByAge(SessionContext context, int keepRecent, Set<String> toolFilter) {
    List<ToolResultLocation> allResults = collectToolResults(context, toolFilter);

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

    return clearToolResultsByIds(context, idsToClear);
  }

  // ── Internal Helper Methods ─────────────────────────────────────────────

  /** Finds the timestamp of the most recent assistant message in the context. */
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

  /** Finds the timestamp of the most recent assistant message in a turn. */
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

  /** Collects all tool results from the context, optionally filtered by tool name. */
  private List<ToolResultLocation> collectToolResults(SessionContext context, Set<String> toolFilter) {
    List<ToolResultLocation> results = new ArrayList<>();
    Set<String> effectiveFilter = toolFilter != null ? toolFilter : DEFAULT_COMPACTABLE_TOOLS;

    // Process previous turns
    for (Turn turn : context.previousTurns()) {
      collectFromTurn(turn, results, effectiveFilter);
    }

    // Process current turn
    if (context.currentTurn() != null) {
      collectFromTurn(context.currentTurn(), results, effectiveFilter);
    }

    return results;
  }

  /** Collects tool results from a single turn. */
  private void collectFromTurn(Turn turn, List<ToolResultLocation> results, Set<String> toolFilter) {
    for (Message msg : turn.intermediateSteps()) {
      if (msg.role() == Role.TOOL
          && msg.toolObservation() != null
          && (toolFilter == null || toolFilter.contains(msg.toolObservation().toolName().toLowerCase()))) {
        results.add(
            new ToolResultLocation(
                msg.toolObservation().toolCallId(),
                msg.toolObservation().toolName(),
                msg.timestamp() != null ? msg.timestamp().toEpochMilli() : turn.timestamp()));
      }
    }
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
        context.modelOptions(),
        context.compressionState());
  }

  /** Clears tool results in a single turn. */
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