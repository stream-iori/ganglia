package work.ganglia.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MarkdownRendererTest {

  private final MarkdownRenderer renderer = new MarkdownRenderer();

  static Stream<Arguments> markdownProvider() {
    return Stream.of(
        arguments(
            "# Title",
            List.of("\u001B[4;36;1mTitle"),
            "Headers should have ANSI colors and underline"),
        arguments("**bold**", List.of("\u001B[1mbold"), "Bold should have ANSI bold code"),
        arguments("*italic*", List.of("\u001B[3mitalic"), "Italic should have ANSI italic code"),
        arguments(
            "```java\nint x = 1;\n```",
            List.of("java", "int x = 1;"),
            "Code blocks should show language and code"),
        arguments(
            "- [ ] todo\n- [x] done",
            List.of("\u2610", "todo", "\u2611", "done"),
            "Checkboxes should render with ballot box symbols"),
        arguments("## Sub", List.of("## Sub"), "H2 should show ## prefix"),
        arguments(
            "1. first\n2. second",
            List.of("1. ", "first", "2. ", "second"),
            "Ordered lists should show numbers"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("markdownProvider")
  @DisplayName("Markdown Rendering Tests")
  void testRendering(String input, List<String> expectedSubstrings, String description) {
    String result = renderer.render(input);
    for (String expected : expectedSubstrings) {
      assertTrue(
          result.contains(expected),
          () ->
              String.format(
                  "Result for '%s' should contain '%s'. Actual result: %s",
                  description, expected, result));
    }
  }

  // ── XML / HTML rendering tests ────────────────────────────────────────

  /** Raw XML block (no fenced code markers) — CommonMark parses it as HtmlBlock. */
  @Test
  @DisplayName("Bare XML block should render as code block, not raw tags")
  void testBareXmlBlockRendersAsCodeBlock() {
    String xml = "<dependency>\n  <groupId>io.vertx</groupId>\n</dependency>";
    String result = renderer.render(xml);
    // Should contain the xml content
    assertTrue(result.contains("groupId"), "Should contain XML content");
    // Should NOT expose raw angle-bracket tags as plain text (i.e. must be styled)
    // The code-block renderer prefixes each line with spaces and CODE_BG styling.
    // A simple heuristic: the rendered output must not be just the bare xml string.
    assertFalse(
        result.equals(xml) || result.equals(xml + "\n"),
        "Should not be an unstyled pass-through of raw XML. Actual: " + result);
    // Must contain ANSI escape code (styling applied)
    assertTrue(
        result.contains("\033["),
        "Should contain ANSI escape codes for styling. Actual: " + result);
    // Must contain the "xml" language label from renderCodeBlock
    assertTrue(result.contains("xml"), "Should contain 'xml' language label. Actual: " + result);
  }

  /** XML wrapped in a fenced code block — should render the same code-block style. */
  @Test
  @DisplayName("Fenced XML code block should render with xml label and code-block style")
  void testFencedXmlCodeBlock() {
    String markdown = "```xml\n<project>\n  <version>1.0</version>\n</project>\n```";
    String result = renderer.render(markdown);
    assertTrue(result.contains("xml"), "Should contain xml language label");
    assertTrue(result.contains("<project>"), "Should contain XML content");
    assertTrue(result.contains("<version>1.0</version>"), "Should contain nested XML content");
  }

  /** Inline HTML tag — CommonMark parses as HtmlInline — should render as inline code style. */
  @Test
  @DisplayName("Inline HTML tag inside paragraph should render as inline code")
  void testInlineHtmlTag() {
    String markdown = "Use the `<dependency>` element.";
    String result = renderer.render(markdown);
    assertTrue(result.contains("dependency"), "Should contain tag content");
  }
}
