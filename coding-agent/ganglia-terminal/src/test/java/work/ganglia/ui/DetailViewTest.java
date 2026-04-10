package work.ganglia.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class DetailViewTest {

  private Terminal terminal;
  private ByteArrayOutputStream output;
  private DetailView detailView;

  @BeforeEach
  void setUp() throws IOException {
    output = new ByteArrayOutputStream();
    terminal =
        TerminalBuilder.builder()
            .system(false)
            .dumb(true)
            .encoding(StandardCharsets.UTF_8)
            .streams(new ByteArrayInputStream(new byte[0]), output)
            .build();
    StatusBar statusBar = new StatusBar(terminal);
    detailView = new DetailView(terminal, statusBar);
  }

  @AfterEach
  void tearDown() throws IOException {
    terminal.close();
  }

  @Test
  void testBuildContentLinesWithAllFields() {
    ToolCard card =
        new ToolCard(
            "run_shell_command",
            "$ git diff",
            List.of("line1", "line2", "line3"),
            "ok",
            false,
            1200);

    List<String> lines = detailView.buildContentLines(card);

    assertEquals("$ git diff", lines.get(0));
    assertEquals("", lines.get(1)); // blank after params
    assertEquals("line1", lines.get(2));
    assertEquals("line2", lines.get(3));
    assertEquals("line3", lines.get(4));
    assertEquals("", lines.get(5)); // blank before result
    assertEquals("ok", lines.get(6));
    assertEquals(7, lines.size());
  }

  @Test
  void testBuildContentLinesNoParams() {
    ToolCard card = new ToolCard("read_file", "", List.of("file content here"), null, false, 500);

    List<String> lines = detailView.buildContentLines(card);

    assertEquals("file content here", lines.get(0));
    assertEquals(1, lines.size());
  }

  @Test
  void testBuildContentLinesEmptyOutput() {
    ToolCard card =
        new ToolCard(
            "read_file", "path=/tmp/test.txt", List.of(), "Error: file not found", true, 100);

    List<String> lines = detailView.buildContentLines(card);

    assertEquals("path=/tmp/test.txt", lines.get(0));
    assertEquals("", lines.get(1));
    // blank line before result
    assertEquals("", lines.get(2));
    assertEquals("Error: file not found", lines.get(3));
    assertEquals(4, lines.size());
  }

  @Test
  void testBuildContentLinesNoResult() {
    ToolCard card =
        new ToolCard("run_shell_command", "$ ls", List.of("a.txt", "b.txt"), null, false, 300);

    List<String> lines = detailView.buildContentLines(card);

    assertEquals("$ ls", lines.get(0));
    assertEquals("", lines.get(1));
    assertEquals("a.txt", lines.get(2));
    assertEquals("b.txt", lines.get(3));
    assertEquals(4, lines.size());
  }

  // ── buildContentLines(String) tests ──────────────────────────────────

  @Test
  @DisplayName("buildContentLines(String) splits on newlines")
  void testBuildContentLinesFromStringSplitsOnNewlines() {
    List<String> lines = detailView.buildContentLines("line1\nline2\nline3");

    assertEquals(3, lines.size());
    assertEquals("line1", lines.get(0));
    assertEquals("line2", lines.get(1));
    assertEquals("line3", lines.get(2));
  }

  @Test
  @DisplayName("buildContentLines(String) returns empty list for null input")
  void testBuildContentLinesFromStringNull() {
    List<String> lines = detailView.buildContentLines((String) null);

    assertTrue(lines.isEmpty());
  }

  @Test
  @DisplayName("buildContentLines(String) returns empty list for empty string")
  void testBuildContentLinesFromStringEmpty() {
    List<String> lines = detailView.buildContentLines("");

    assertTrue(lines.isEmpty());
  }

  @Test
  @DisplayName("buildContentLines(String) preserves blank lines within content")
  void testBuildContentLinesFromStringPreservesBlankLines() {
    List<String> lines = detailView.buildContentLines("first\n\nthird");

    assertEquals(3, lines.size());
    assertEquals("first", lines.get(0));
    assertEquals("", lines.get(1));
    assertEquals("third", lines.get(2));
  }

  @Test
  @DisplayName("buildContentLines(String) handles single line with no newline")
  void testBuildContentLinesFromStringSingleLine() {
    List<String> lines = detailView.buildContentLines("only one line");

    assertEquals(1, lines.size());
    assertEquals("only one line", lines.get(0));
  }

  // ── show(String, String) tests ────────────────────────────────────────

  @Test
  @DisplayName("show(title, content) on dumb terminal prints inline without throwing")
  void testShowResponseOverlayDumbTerminalPrintsInline() throws IOException {
    // Dumb terminal skips the alternate-screen overlay and prints inline.
    // No input is needed since nothing blocks for user input.
    assertDoesNotThrow(() -> detailView.show("Response", "Hello\nWorld"));

    String written = output.toString(StandardCharsets.UTF_8);
    assertTrue(written.contains("Response"), "Should print title");
    assertTrue(written.contains("Hello"), "Should print content line 1");
    assertTrue(written.contains("World"), "Should print content line 2");
  }

  @Test
  @DisplayName("show(title, content) on dumb terminal with empty content completes without error")
  void testShowResponseOverlayDumbTerminalEmptyContent() {
    assertDoesNotThrow(() -> detailView.show("Empty", ""));
  }

  @Test
  @DisplayName(
      "show(title, content) on non-dumb terminal emits alternate-screen sequences and exits on 'q'")
  void testShowResponseOverlayAltScreenSequences() throws IOException {
    // Use a non-dumb terminal with 'q' as input so the overlay reads one key and exits.
    byte[] input = "q".getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream nonDumbOutput = new ByteArrayOutputStream();
    Terminal nonDumbTerminal =
        TerminalBuilder.builder()
            .system(false)
            .type("xterm-256color")
            .encoding(StandardCharsets.UTF_8)
            .streams(new ByteArrayInputStream(input), nonDumbOutput)
            .build();
    StatusBar sb = new StatusBar(nonDumbTerminal);
    DetailView dv = new DetailView(nonDumbTerminal, sb);

    assertDoesNotThrow(() -> dv.show("Response", "line1\nline2\nline3"));

    // Close the terminal first to ensure all internal buffers are fully flushed
    // to the underlying ByteArrayOutputStream before inspecting the output.
    nonDumbTerminal.close();

    String written = nonDumbOutput.toString(StandardCharsets.UTF_8);
    // Alternate screen enter/exit sequences must be present
    assertTrue(written.contains("\033[?1049h"), "Should emit enter-alt-screen sequence");
    assertTrue(written.contains("\033[?1049l"), "Should emit exit-alt-screen sequence");
  }

  @Test
  @DisplayName("show(ToolCard) on non-dumb terminal emits alternate-screen sequences")
  void testShowToolCardAltScreenSequences() throws IOException {
    byte[] input = "q".getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream nonDumbOutput = new ByteArrayOutputStream();
    Terminal nonDumbTerminal =
        TerminalBuilder.builder()
            .system(false)
            .type("xterm-256color")
            .encoding(StandardCharsets.UTF_8)
            .streams(new ByteArrayInputStream(input), nonDumbOutput)
            .build();
    StatusBar sb = new StatusBar(nonDumbTerminal);
    DetailView dv = new DetailView(nonDumbTerminal, sb);

    ToolCard card =
        new ToolCard(
            "read_file", "path=pom.xml", List.of("<project>", "</project>"), "ok", false, 50);
    assertDoesNotThrow(() -> dv.show(card));

    nonDumbTerminal.close();

    String written = nonDumbOutput.toString(StandardCharsets.UTF_8);
    assertTrue(written.contains("\033[?1049h"), "Should emit enter-alt-screen sequence");
    assertTrue(written.contains("\033[?1049l"), "Should emit exit-alt-screen sequence");
  }
}
