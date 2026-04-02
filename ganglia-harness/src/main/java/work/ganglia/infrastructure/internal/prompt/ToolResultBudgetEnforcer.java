package work.ganglia.infrastructure.internal.prompt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.util.TokenCounter;

/**
 * Enforces aggregate budget for tool outputs within a turn. Uses replacement caching for prompt
 * cache stability - once a tool result is replaced, the same replacement is used in subsequent
 * turns to maintain prompt cache hits.
 */
public class ToolResultBudgetEnforcer {
  private static final Logger logger = LoggerFactory.getLogger(ToolResultBudgetEnforcer.class);

  /** Number of recent tool results to always preserve unchanged */
  private static final int PRESERVE_RECENT_COUNT = 3;

  private final TokenCounter tokenCounter;
  private final int aggregateBudget;

  /**
   * Replacement cache: toolCallId -> replacement content. Ensures same tool result gets same
   * replacement across turns (cache stability).
   */
  private final Map<String, String> replacementCache = new ConcurrentHashMap<>();

  public ToolResultBudgetEnforcer(TokenCounter tokenCounter, int aggregateBudget) {
    this.tokenCounter = tokenCounter;
    this.aggregateBudget = aggregateBudget;
  }

  /**
   * Enforces aggregate budget on tool messages. Returns possibly modified list of messages.
   *
   * @param messages the input messages
   * @return messages with tool outputs potentially truncated to fit aggregate budget
   */
  public List<Message> enforce(List<Message> messages) {
    // Collect all TOOL messages with their indices
    List<ToolMessageInfo> toolMessages = new ArrayList<>();
    for (int i = 0; i < messages.size(); i++) {
      Message m = messages.get(i);
      if (m.role() == Role.TOOL && m.toolObservation() != null) {
        String id = m.toolObservation().toolCallId();
        int tokens = tokenCounter.count(m.content());
        toolMessages.add(new ToolMessageInfo(i, id, m, tokens));
      }
    }

    if (toolMessages.isEmpty()) {
      return messages;
    }

    int totalTokens = toolMessages.stream().mapToInt(t -> t.tokens).sum();

    if (totalTokens <= aggregateBudget) {
      return messages; // Within budget
    }

    logger.info(
        "Tool result aggregate budget exceeded: {} > {}. Truncating oldest results.",
        totalTokens,
        aggregateBudget);

    // Identify indices to preserve (most recent N)
    Set<Integer> preserveIndices = new HashSet<>();
    for (int i = Math.max(0, toolMessages.size() - PRESERVE_RECENT_COUNT);
        i < toolMessages.size();
        i++) {
      preserveIndices.add(toolMessages.get(i).index);
    }

    // Truncate from oldest
    List<Message> result = new ArrayList<>(messages);
    int currentTotal = totalTokens;

    for (ToolMessageInfo info : toolMessages) {
      if (currentTotal <= aggregateBudget) break;
      if (preserveIndices.contains(info.index)) continue;

      String toolCallId = info.toolCallId;
      String originalContent = info.message.content();

      // Check cache first for prompt cache stability
      String replacement = replacementCache.get(toolCallId);

      if (replacement == null) {
        // Calculate replacement: reduce to half of original
        int targetTokens = Math.max(500, info.tokens / 2);
        replacement = truncateContent(originalContent, targetTokens, info.toolName());
        replacementCache.put(toolCallId, replacement);
      }

      // Replace message
      Message newMsg = Message.toolCapped(toolCallId, info.toolName(), replacement);
      result.set(info.index, newMsg);

      int newTokens = tokenCounter.count(replacement);
      currentTotal -= (info.tokens - newTokens);
    }

    return result;
  }

  private String truncateContent(String content, int maxTokens, String toolName) {
    if (tokenCounter.count(content) <= maxTokens) {
      return content;
    }

    // Binary search for token boundary
    int lo = 0, hi = content.length();
    while (lo < hi - 1) {
      int mid = (lo + hi) / 2;
      if (tokenCounter.count(content.substring(0, mid)) <= maxTokens) {
        lo = mid;
      } else {
        hi = mid;
      }
    }

    return content.substring(0, lo)
        + "\n\n[TRUNCATED: output from '"
        + toolName
        + "' exceeded aggregate budget. "
        + "Use the tool with different parameters to see full output.]";
  }

  /** Clears the replacement cache (call when session ends). */
  public void clearCache() {
    replacementCache.clear();
  }

  /** Returns the current cache size for monitoring. */
  public int getCacheSize() {
    return replacementCache.size();
  }

  private record ToolMessageInfo(int index, String toolCallId, Message message, int tokens) {
    String toolName() {
      return message.toolObservation().toolName();
    }
  }
}
