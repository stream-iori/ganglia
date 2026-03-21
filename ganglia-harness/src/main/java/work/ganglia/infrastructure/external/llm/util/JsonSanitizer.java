package work.ganglia.infrastructure.external.llm.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility to clean up and fix common JSON issues in LLM outputs. */
public class JsonSanitizer {

  private static final Pattern MARKDOWN_JSON_BLOCK =
      Pattern.compile("(?s)```json\\s*(.*?)\\s*```", Pattern.CASE_INSENSITIVE);

  /**
   * Attempts to extract valid JSON from a string that might contain Markdown blocks or extra text.
   */
  public static String sanitize(String input) {
    if (input == null || input.isBlank()) {
      return "{}";
    }

    String cleaned = input.trim();

    // 1. Try to extract from Markdown block
    Matcher matcher = MARKDOWN_JSON_BLOCK.matcher(cleaned);
    if (matcher.find()) {
      cleaned = matcher.group(1).trim();
    }

    // 2. If it doesn't already look like a JSON object/array, try to find the boundaries.
    // We check startsWith on the already trimmed string.
    if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
      int startObj = cleaned.indexOf('{');
      int startArray = cleaned.indexOf('[');

      int start = -1;
      if (startObj != -1 && (startArray == -1 || startObj < startArray)) {
        start = startObj;
      } else {
        start = startArray;
      }

      if (start != -1) {
        int end =
            (cleaned.charAt(start) == '{') ? cleaned.lastIndexOf('}') : cleaned.lastIndexOf(']');
        if (end > start) {
          cleaned = cleaned.substring(start, end + 1);
        }
      }
    }

    // 3. Escape raw control characters inside string literals.
    // This MUST be done before any other string manipulations that might add/remove quotes.
    cleaned = escapeControlCharsInStrings(cleaned);

    // 4. Basic cleanup of common LLM errors (e.g., trailing commas in objects/arrays)
    cleaned = cleaned.replaceAll(",\\s*([}\\]])", "$1");

    return cleaned;
  }

  /**
   * Robustly escapes raw control characters (like newlines) that appear inside double-quoted
   * strings in a JSON-like string.
   */
  private static String escapeControlCharsInStrings(String input) {
    StringBuilder sb = new StringBuilder();
    boolean inString = false;
    boolean escaped = false;
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      if (c == '"' && !escaped) {
        inString = !inString;
      }

      if (inString) {
        switch (c) {
          case '\n' -> sb.append("\\n");
          case '\r' -> sb.append("\\r");
          case '\t' -> sb.append("\\t");
          case '\b' -> sb.append("\\b");
          case '\f' -> sb.append("\\f");
          default -> {
            if (c < 32) {
              sb.append(String.format("\\u%04x", (int) c));
            } else {
              sb.append(c);
            }
          }
        }
      } else {
        sb.append(c);
      }

      if (c == '\\' && !escaped) {
        escaped = true;
      } else {
        escaped = false;
      }
    }
    return sb.toString();
  }
}
