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
  void testInteractionToolsSchema() {
    String schema =
        """
  {
    "type": "object",
    "required": ["questions"],
    "properties": {
      "questions": {
        "type": "array",
        "minItems": 1,
        "maxItems": 4,
        "description": "A list of questions to ask the user to gather preferences, clarify requirements, or make decisions.",
        "items": {
          "type": "object",
          "required": ["question", "header", "type"],
          "properties": {
            "question": {
              "type": "string",
              "description": "The complete question to ask the user. Should be clear, specific, and end with a question mark."
            },
            "header": {
              "type": "string",
              "description": "Very short label (max 16 chars) displayed as a chip/tag. Use abbreviations (e.g., 'Auth', 'Config',
  'Database')."
            },
            "type": {
              "type": "string",
              "enum": ["choice", "text", "yesno"],
              "default": "choice",
              "description": "Question type: 'choice' (multiple-choice), 'text' (free-form), 'yesno' (Yes/No confirmation)."
            },
            "options": {
              "type": "array",
              "description": "Selectable choices for 'choice' type. Provide 2-4 options. An 'Other' option is automatically added. Not
  needed for 'text' or 'yesno'.",
              "items": {
                "type": "object",
                "required": ["label", "description"],
                "properties": {
                  "label": {
                    "type": "string",
                    "description": "Display text for the option (1-5 words)."
                  },
                  "description": {
                    "type": "string",
                    "description": "Brief explanation of what this option entails."
                  }
                }
              }
            },
            "multiSelect": {
              "type": "boolean",
              "description": "If true, allows the user to select multiple options (only for 'choice' type)."
            },
            "placeholder": {
              "type": "string",
              "description": "Hint text shown in the input field (only for 'text' type)."
            }
          }
        }
      }
    }
  }
                """;
    String sanitized = JsonSanitizer.sanitize(schema);
    JsonObject json = new JsonObject(sanitized);
    assertNotNull(json);
  }

  @Test
  void testBraceInsideString() {
    String input = " Junk prefix {\"foo\": \"brace } here\"} Junk suffix";
    String sanitized = JsonSanitizer.sanitize(input);
    // Our improved logic now extracts this correctly!
    JsonObject json = new JsonObject(sanitized);
    assertEquals("brace } here", json.getString("foo"));
  }

  @Test
  void testEmptyInput() {
    assertEquals("{}", JsonSanitizer.sanitize(null));
    assertEquals("{}", JsonSanitizer.sanitize("   "));
  }
}
