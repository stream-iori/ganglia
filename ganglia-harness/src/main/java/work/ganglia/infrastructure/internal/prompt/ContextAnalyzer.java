package work.ganglia.infrastructure.internal.prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextAnalysis;
import work.ganglia.util.TokenCounter;

/**
 * Analyzes token distribution across context components.
 *
 * <p>Provides visibility into what's consuming context: tool results, messages, system prompt, etc.
 */
public class ContextAnalyzer {
  private static final Pattern THINKING_PATTERN =
      Pattern.compile("<thinking>.*?</thinking>", Pattern.DOTALL);

  private final TokenCounter tokenCounter;

  public ContextAnalyzer(TokenCounter tokenCounter) {
    this.tokenCounter = tokenCounter;
  }

  /**
   * Analyzes token distribution for a session context and system prompt.
   *
   * @param context the session context to analyze
   * @param systemPrompt the system prompt content
   * @return a ContextAnalysis with token breakdowns
   */
  public ContextAnalysis analyze(SessionContext context, String systemPrompt) {
    int systemPromptTokens = systemPrompt != null ? tokenCounter.count(systemPrompt) : 0;

    Map<String, Integer> toolRequestTokens = new HashMap<>();
    Map<String, Integer> toolResultTokens = new HashMap<>();
    int userMessageTokens = 0;
    int assistantMessageTokens = 0;
    int thinkingTokens = 0;

    List<Message> history = context.history();
    for (Message msg : history) {
      int msgTokens = msg.countTokens(tokenCounter);

      switch (msg.role()) {
        case USER -> userMessageTokens += msgTokens;
        case ASSISTANT -> {
          assistantMessageTokens += msgTokens;

          // Count tool call tokens
          if (msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
            for (var call : msg.toolCalls()) {
              String toolName = call.toolName();
              int callTokens = tokenCounter.count(call.toString());
              toolRequestTokens.merge(toolName, callTokens, Integer::sum);
            }
          }

          // Count thinking tokens
          if (msg.content() != null) {
            int thinking = countThinkingTokens(msg.content());
            thinkingTokens += thinking;
          }
        }
        case TOOL -> {
          String toolName =
              msg.toolObservation() != null ? msg.toolObservation().toolName() : "unknown";
          toolResultTokens.merge(toolName, msgTokens, Integer::sum);
        }
        case SYSTEM -> {
          // System messages in history (like summaries) count as assistant for simplicity
          assistantMessageTokens += msgTokens;
        }
      }
    }

    int historyTokens =
        userMessageTokens
            + assistantMessageTokens
            + toolResultTokens.values().stream().mapToInt(Integer::intValue).sum();
    int totalTokens = systemPromptTokens + historyTokens;

    return new ContextAnalysis(
        totalTokens,
        systemPromptTokens,
        historyTokens,
        toolRequestTokens,
        toolResultTokens,
        userMessageTokens,
        assistantMessageTokens,
        thinkingTokens);
  }

  /** Counts tokens within thinking blocks. */
  private int countThinkingTokens(String content) {
    var matcher = THINKING_PATTERN.matcher(content);
    int tokens = 0;
    while (matcher.find()) {
      tokens += tokenCounter.count(matcher.group());
    }
    return tokens;
  }
}
