package work.ganglia.port.internal.prompt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Analysis of token distribution across context components.
 *
 * <p>Provides visibility into what's consuming context: tool results, messages, system prompt, etc.
 */
public record ContextAnalysis(
    int totalTokens,
    int systemPromptTokens,
    int historyTokens,
    Map<String, Integer> toolRequestTokens, // toolName -> tokens
    Map<String, Integer> toolResultTokens, // toolName -> tokens
    int userMessageTokens,
    int assistantMessageTokens,
    int thinkingTokens) {

  /** Returns an empty analysis with all zeros. */
  public static ContextAnalysis empty() {
    return new ContextAnalysis(0, 0, 0, Collections.emptyMap(), Collections.emptyMap(), 0, 0, 0);
  }

  /** Converts the analysis to a map suitable for observation data. */
  public Map<String, Object> toObservationData() {
    Map<String, Object> data = new HashMap<>();
    data.put("totalTokens", totalTokens);
    data.put("systemPromptTokens", systemPromptTokens);
    data.put("historyTokens", historyTokens);
    data.put("toolRequestTokens", toolRequestTokens);
    data.put("toolResultTokens", toolResultTokens);
    data.put("userMessageTokens", userMessageTokens);
    data.put("assistantMessageTokens", assistantMessageTokens);
    data.put("thinkingTokens", thinkingTokens);

    // Calculate derived metrics
    int toolTokens =
        toolRequestTokens.values().stream().mapToInt(Integer::intValue).sum()
            + toolResultTokens.values().stream().mapToInt(Integer::intValue).sum();
    data.put("toolTokensTotal", toolTokens);

    return data;
  }
}
