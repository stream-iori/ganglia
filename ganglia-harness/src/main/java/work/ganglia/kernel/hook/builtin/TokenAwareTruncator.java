package work.ganglia.kernel.hook.builtin;

import work.ganglia.util.TokenCounter;

/**
 * Truncates text to a maximum number of tokens, appending a truncation notice. Used as a fallback
 * when LLM-based compression is unavailable or fails.
 */
public class TokenAwareTruncator {
  private final TokenCounter tokenCounter;
  private final int maxTokens;

  public TokenAwareTruncator(TokenCounter tokenCounter, int maxTokens) {
    this.tokenCounter = tokenCounter;
    this.maxTokens = maxTokens;
  }

  public int getMaxTokens() {
    return maxTokens;
  }

  /**
   * Truncates {@code text} so that the result contains at most {@code maxTokens} tokens. If the
   * text is already within the limit it is returned unchanged. A truncation notice is appended when
   * truncation occurs.
   */
  public String truncate(String text, String toolName) {
    return truncate(text, toolName, null);
  }

  /**
   * Truncates {@code text} to at most {@code maxTokens} tokens. When truncation occurs a notice is
   * appended, optionally followed by {@code rerunHint} (e.g. a suggestion to re-invoke the tool
   * with pagination parameters). Pass {@code null} for no hint.
   */
  public String truncate(String text, String toolName, String rerunHint) {
    if (text == null || text.isEmpty()) return text;
    int total = tokenCounter.count(text);
    if (total <= maxTokens) return text;

    // Binary-search for the largest character prefix whose token count ≤ maxTokens
    int lo = 0;
    int hi = text.length();
    while (lo < hi - 1) {
      int mid = (lo + hi) / 2;
      if (tokenCounter.count(text.substring(0, mid)) <= maxTokens) {
        lo = mid;
      } else {
        hi = mid;
      }
    }

    String truncated = text.substring(0, lo);
    int totalLines = countLines(text);
    int shownLines = countLines(truncated);
    String notice =
        "\n\n[TRUNCATED: output from '"
            + toolName
            + "' exceeded "
            + maxTokens
            + " tokens. Showing first "
            + shownLines
            + " of "
            + totalLines
            + " lines ("
            + tokenCounter.count(truncated)
            + "/"
            + total
            + " tokens)."
            + (rerunHint != null && !rerunHint.isEmpty() ? " " + rerunHint : "")
            + "]";
    return truncated + notice;
  }

  private static int countLines(String text) {
    if (text == null || text.isEmpty()) return 0;
    int count = 1;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') count++;
    }
    return count;
  }
}
