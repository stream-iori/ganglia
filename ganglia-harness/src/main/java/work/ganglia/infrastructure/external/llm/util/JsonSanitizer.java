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

    // 2. Remove leading/trailing non-JSON characters if it starts with { or [
    int start = cleaned.indexOf('{');
    int last = cleaned.lastIndexOf('}');

    if (start == -1) {
      start = cleaned.indexOf('[');
      last = cleaned.lastIndexOf(']');
    }

    if (start != -1 && last != -1 && last > start) {
      cleaned = cleaned.substring(start, last + 1);
    }

    // 3. Basic cleanup of common LLM errors (e.g., trailing commas in objects/arrays)
    cleaned = cleaned.replaceAll(",\\s*([}\\]])", "$1");

    return cleaned;
  }
}
