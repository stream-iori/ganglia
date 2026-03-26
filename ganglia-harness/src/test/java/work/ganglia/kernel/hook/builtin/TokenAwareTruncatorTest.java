package work.ganglia.kernel.hook.builtin;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.ganglia.util.TokenCounter;

class TokenAwareTruncatorTest {

  private TokenCounter tokenCounter;
  private TokenAwareTruncator truncator;

  @BeforeEach
  void setUp() {
    tokenCounter = new TokenCounter();
    truncator = new TokenAwareTruncator(tokenCounter, 100);
  }

  @Test
  void shortTextIsReturnedUnchanged() {
    String text = "hello world";
    assertEquals(text, truncator.truncate(text, "my_tool"));
  }

  @Test
  void longTextIsTruncatedToMaxTokens() {
    // ~4 tokens per repeat of "hello world " ≈ 400+ tokens total
    String text = "hello world ".repeat(100);
    String result = truncator.truncate(text, "my_tool");

    assertNotEquals(text, result);
    // Result (without notice) must be within maxTokens
    int noticeStart = result.lastIndexOf("\n\n[TRUNCATED:");
    assertTrue(noticeStart >= 0, "Truncation notice must be present");
    String contentPart = result.substring(0, noticeStart);
    assertTrue(tokenCounter.count(contentPart) <= 100, "Content tokens must be ≤ maxTokens");
  }

  @Test
  void truncationNoticeContainsToolName() {
    String text = "word ".repeat(200);
    String result = truncator.truncate(text, "bash_tool");
    assertTrue(result.contains("bash_tool"), "Notice must mention the tool name");
  }

  @Test
  void truncationNoticeContainsMaxTokens() {
    String text = "word ".repeat(200);
    String result = truncator.truncate(text, "t");
    assertTrue(result.contains("100 tokens"), "Notice must mention the token limit");
  }

  @Test
  void nullTextIsReturnedAsNull() {
    assertNull(truncator.truncate(null, "t"));
  }

  @Test
  void emptyTextIsReturnedUnchanged() {
    assertEquals("", truncator.truncate("", "t"));
  }

  @Test
  void exactlyAtLimitIsNotTruncated() {
    // Build text that is exactly maxTokens
    StringBuilder sb = new StringBuilder();
    while (tokenCounter.count(sb.toString()) < 100) {
      sb.append("a ");
    }
    // Back off one token so we are at or below limit
    while (tokenCounter.count(sb.toString()) > 100) {
      sb.setLength(sb.length() - 2);
    }
    String text = sb.toString();
    assertTrue(tokenCounter.count(text) <= 100);
    assertEquals(text, truncator.truncate(text, "t"));
  }
}
