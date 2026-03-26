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
    detailView = new DetailView(terminal);
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
}
