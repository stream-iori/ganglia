package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.util.TokenCounter;

/**
 * Zero-cost context slimming operations executed before LLM compression.
 *
 * <p>Three operations:
 *
 * <ol>
 *   <li>stripOldThinkingBlocks - Remove thinking blocks from old turns
 *   <li>compactOldToolCallArgs - Replace old tool call arguments with summaries
 *   <li>deduplicateSystemMessages - Merge multiple summary turns
 * </ol>
 */
public class ContextSlimmer {
  private static final Logger logger = LoggerFactory.getLogger(ContextSlimmer.class);

  // Pattern for thinking blocks (Claude-style extended thinking)
  private static final Pattern THINKING_PATTERN =
      Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL);

  // Pattern for thinking content blocks - matches lines starting with thinking marker
  private static final Pattern THOUGHT_CONTENT_PATTERN =
      Pattern.compile("^thinking:.*?(?=\n\n|$)", Pattern.DOTALL | Pattern.MULTILINE);

  private final TokenCounter tokenCounter;

  public ContextSlimmer(TokenCounter tokenCounter) {
    this.tokenCounter = tokenCounter;
  }

  /**
   * Applies all slimming operations and returns the trimmed context. Returns the original context
   * if no reductions are possible.
   */
  public SessionContext slimIfNeeded(SessionContext context) {
    if (context.previousTurns().isEmpty()) {
      return context;
    }

    int beforeTokens = calculateTokens(context);
    SessionContext current = context;

    // Step 1: Strip thinking blocks from old turns (keep last 1 turn intact)
    current = stripOldThinkingBlocks(current, 1);

    // Step 2: Compact tool call arguments (keep last 2 turns intact)
    current = compactOldToolCallArgs(current, 2);

    // Step 3: Deduplicate summary turns
    current = deduplicateSystemMessages(current);

    int afterTokens = calculateTokens(current);
    int saved = beforeTokens - afterTokens;

    if (saved > 0) {
      logger.info("Context slimming saved {} tokens ({} -> {})", saved, beforeTokens, afterTokens);
    }

    return current;
  }

  /**
   * Removes thinking blocks from assistant messages in turns older than keepRecentTurns.
   *
   * @param context the session context
   * @param keepRecentTurns number of most recent turns to preserve unchanged
   */
  public SessionContext stripOldThinkingBlocks(SessionContext context, int keepRecentTurns) {
    List<Turn> turns = context.previousTurns();
    if (turns.size() <= keepRecentTurns) {
      return context;
    }

    List<Turn> newTurns = new ArrayList<>();
    int slimStartIndex = turns.size() - keepRecentTurns;

    for (int i = 0; i < turns.size(); i++) {
      Turn turn = turns.get(i);
      if (i >= slimStartIndex) {
        // Keep recent turns as-is
        newTurns.add(turn);
        continue;
      }

      Turn slimmedTurn = stripThinkingFromTurn(turn);
      newTurns.add(slimmedTurn);
    }

    return context.withPreviousTurns(newTurns);
  }

  private Turn stripThinkingFromTurn(Turn turn) {
    List<Message> newSteps = new ArrayList<>();

    // Process user message
    if (turn.userMessage() != null) {
      newSteps.add(turn.userMessage());
    }

    // Process intermediate steps
    for (Message msg : turn.intermediateSteps()) {
      if (msg.role() == Role.ASSISTANT && msg.content() != null) {
        String newContent = THINKING_PATTERN.matcher(msg.content()).replaceAll("");
        newContent = THOUGHT_CONTENT_PATTERN.matcher(newContent).replaceAll("");
        newContent = newContent.trim();
        if (!newContent.equals(msg.content())) {
          msg = Message.assistant(newContent, msg.toolCalls());
        }
      }
      newSteps.add(msg);
    }

    // Process final response
    if (turn.finalResponse() != null) {
      Message response = turn.finalResponse();
      if (response.role() == Role.ASSISTANT && response.content() != null) {
        String newContent = THINKING_PATTERN.matcher(response.content()).replaceAll("");
        newContent = THOUGHT_CONTENT_PATTERN.matcher(newContent).replaceAll("");
        newContent = newContent.trim();
        if (!newContent.equals(response.content())) {
          response = Message.assistant(newContent, response.toolCalls());
        }
      }
      newSteps.add(response);
    }

    return new Turn(
        turn.id(),
        turn.userMessage(),
        turn.intermediateSteps(),
        turn.finalResponse(),
        turn.timestamp());
  }

  /**
   * Replaces tool call arguments with summary form in turns older than keepRecentTurns.
   *
   * @param context the session context
   * @param keepRecentTurns number of most recent turns to preserve unchanged
   */
  public SessionContext compactOldToolCallArgs(SessionContext context, int keepRecentTurns) {
    List<Turn> turns = context.previousTurns();
    if (turns.size() <= keepRecentTurns) {
      return context;
    }

    List<Turn> newTurns = new ArrayList<>();
    int compactStartIndex = turns.size() - keepRecentTurns;

    for (int i = 0; i < turns.size(); i++) {
      Turn turn = turns.get(i);
      if (i >= compactStartIndex) {
        newTurns.add(turn);
        continue;
      }

      Turn compactedTurn = compactToolArgsFromTurn(turn);
      newTurns.add(compactedTurn);
    }

    return context.withPreviousTurns(newTurns);
  }

  private Turn compactToolArgsFromTurn(Turn turn) {
    List<Message> newSteps = new ArrayList<>();

    for (Message msg : turn.intermediateSteps()) {
      if (msg.role() == Role.ASSISTANT && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
        List<ToolCall> compactedCalls =
            msg.toolCalls().stream().map(this::compactToolCall).collect(Collectors.toList());
        msg = Message.assistant(msg.content(), compactedCalls);
      }
      newSteps.add(msg);
    }

    return turn.withIntermediateSteps(newSteps);
  }

  private ToolCall compactToolCall(ToolCall call) {
    if (call.arguments() == null || call.arguments().isEmpty()) {
      return call;
    }
    // Replace arguments with summary showing just the keys
    String argKeys = String.join(", ", call.arguments().keySet());
    String summary = call.toolName() + "(" + argKeys + ")";
    return new ToolCall(call.id(), call.toolName(), Map.of("_summary", summary));
  }

  /**
   * Merges multiple summary turns into a single one.
   *
   * @param context the session context
   */
  public SessionContext deduplicateSystemMessages(SessionContext context) {
    List<Turn> turns = context.previousTurns();

    // Find all summary turns (those starting with "summary-" id)
    List<Turn> summaryTurns =
        turns.stream().filter(t -> t.id().startsWith("summary-")).collect(Collectors.toList());

    if (summaryTurns.size() <= 1) {
      return context;
    }

    // Merge summaries
    StringBuilder merged = new StringBuilder();
    for (Turn t : summaryTurns) {
      // Get content from the summary turn
      if (t.userMessage() != null && t.userMessage().content() != null) {
        merged.append(t.userMessage().content()).append("\n\n");
      } else if (!t.intermediateSteps().isEmpty()) {
        Message first = t.intermediateSteps().get(0);
        if (first.content() != null) {
          merged.append(first.content()).append("\n\n");
        }
      }
    }

    // Create single summary turn
    Message summaryMsg =
        Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + merged.toString().trim());
    Turn mergedTurn = Turn.newTurn("summary-merged-" + System.currentTimeMillis(), summaryMsg);

    // Replace all summaries with merged one, preserving non-summary turns
    List<Turn> newTurns = new ArrayList<>();
    newTurns.add(mergedTurn);
    turns.stream().filter(t -> !t.id().startsWith("summary-")).forEach(newTurns::add);

    return context.withPreviousTurns(newTurns);
  }

  private int calculateTokens(SessionContext context) {
    return context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
  }
}
