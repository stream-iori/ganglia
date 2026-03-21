package work.ganglia.infrastructure.external.llm.util;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

public class JsonSanitizerTest {

  @Test
  void testEscapeNewlines() {
    String input = "{\n  \"foo\": \"line 1\nline 2\"\n}";
    String sanitized = JsonSanitizer.sanitize(input);
    JsonObject json = new JsonObject(sanitized);
    assertEquals("line 1\nline 2", json.getString("foo"));
  }

  @Test
  void testEscapeMixedControlChars() {
    String input = "{\"text\": \"Tabs\there, and\r\nnewlines.\" }";
    String sanitized = JsonSanitizer.sanitize(input);
    JsonObject json = new JsonObject(sanitized);
    assertEquals("Tabs\there, and\r\nnewlines.", json.getString("text"));
  }

  @Test
  void testMarkdownExtractionAndEscaping() {
    String input = "Here is the result:\n```json\n{\"msg\": \"Hello\nWorld\"}\n```";
    String sanitized = JsonSanitizer.sanitize(input);
    JsonObject json = new JsonObject(sanitized);
    assertEquals("Hello\nWorld", json.getString("msg"));
  }

  @Test
  void testHandleEscapedQuotesInsideStrings() {
    String input = "{\"key\": \"Value with \\\"quoted\\\" text and \n newlines\"}";
    String sanitized = JsonSanitizer.sanitize(input);
    JsonObject json = new JsonObject(sanitized);
    assertEquals("Value with \"quoted\" text and \n newlines", json.getString("key"));
  }

  @Test
  void testEmptyInput() {
    assertEquals("{}", JsonSanitizer.sanitize(null));
    assertEquals("{}", JsonSanitizer.sanitize("   "));
  }
}
