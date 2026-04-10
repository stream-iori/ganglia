package work.ganglia.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.ganglia.kernel.todo.TaskStatus;
import work.ganglia.kernel.todo.ToDoItem;
import work.ganglia.kernel.todo.ToDoList;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;

public class EventRendererTest {

  private Terminal terminal;
  private ByteArrayOutputStream output;
  private EventRenderer renderer;
  private StatusBar statusBar;

  @BeforeEach
  void setUp() throws IOException {
    output = new ByteArrayOutputStream();
    terminal =
        TerminalBuilder.builder()
            .system(false)
            .dumb(true)
            .jansi(false)
            .jna(false)
            .encoding(StandardCharsets.UTF_8)
            .streams(new ByteArrayInputStream(new byte[0]), output)
            .build();
    statusBar = new StatusBar(terminal);
    MarkdownRenderer mdRenderer = new MarkdownRenderer();
    renderer = new EventRenderer(terminal, mdRenderer, statusBar);
  }

  @AfterEach
  void tearDown() throws IOException {
    terminal.close();
  }

  @Test
  void testTurnStartedClearsAccumulator() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "hello", null));
    assertEquals("hello", renderer.getAccumulatedTokens().toString());

    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    assertEquals("", renderer.getAccumulatedTokens().toString());
  }

  @Test
  void testTokenReceivedAccumulates() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "hello ", null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "world", null));
    assertEquals("hello world", renderer.getAccumulatedTokens().toString());
  }

  @Test
  void testToolCardRendering() {
    renderer.render(
        ObservationEvent.of(
            "s1",
            ObservationType.TOOL_STARTED,
            "run_shell_command",
            Map.of("command", "git status")));
    String outputStr = stripAnsi(getOutput());

    assertTrue(
        outputStr.contains("run_shell_command"), "Should contain tool name. Output: " + outputStr);
    assertTrue(
        outputStr.contains("$ git status"),
        "Should contain command with $ prefix. Output: " + outputStr);
  }

  @Test
  void testToolFinishedClosesCard() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "read_file", Map.of()));
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, "ok", null));

    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("\u2713"), "Should contain checkmark for success. Output: " + outputStr);
  }

  @Test
  void testToolFinishedError() {
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "run_shell_command", Map.of()));
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, "Error: command failed", null));

    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("\u2717"), "Should contain X mark for failure. Output: " + outputStr);
  }

  @Test
  void testToolOutputStream() {
    renderer.render(
        ObservationEvent.of(
            "s1", ObservationType.TOOL_STARTED, "run_shell_command", Map.of("command", "ls")));
    renderer.render(
        ObservationEvent.of(
            "s1", ObservationType.TOOL_OUTPUT_STREAM, "file1.txt\nfile2.txt", null));

    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("file1.txt"), "Should contain first output line. Output: " + outputStr);
    assertTrue(
        outputStr.contains("file2.txt"), "Should contain second output line. Output: " + outputStr);
  }

  @Test
  void testReasoningStartedUpdatesStatusBar() {
    renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_STARTED, null, null));
    assertEquals("\u23f3 Thinking...", statusBar.getCurrentStatus(), "Status should be thinking");
  }

  @Test
  void testTurnFinishedSetsIdle() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));
    assertEquals("\u2713 Ready", statusBar.getCurrentStatus(), "Status should be ready");
  }

  @Test
  void testErrorRendering() {
    renderer.render(ObservationEvent.of("s1", ObservationType.ERROR, "Something went wrong", null));
    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("Something went wrong"),
        "Should contain error message. Output: " + outputStr);
  }

  @Test
  void testTurnFinishedRendersResponseWithDot() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Hello there!", null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

    String outputStr = stripAnsi(getOutput());
    // Response dot prefix: ●
    assertTrue(
        outputStr.contains("\u25cf"),
        "Should contain green dot ● for response. Output: " + outputStr);
    assertTrue(
        outputStr.contains("Hello there"), "Should contain response content. Output: " + outputStr);
  }

  @Test
  void testResponseTruncation() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    StringBuilder longContent = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      longContent.append("Line number ").append(i).append("\n\n");
    }
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, longContent.toString(), null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("..."), "Should contain truncation ellipsis. Output: " + outputStr);
    assertTrue(
        outputStr.contains("lines"), "Should mention remaining lines count. Output: " + outputStr);
    assertTrue(
        outputStr.contains("Ctrl+O"), "Should mention Ctrl+O to expand. Output: " + outputStr);
  }

  @Test
  void testGetLastRenderedResponse() {
    assertNull(renderer.getLastRenderedResponse(), "Should be null before any turn");

    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Test response", null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

    assertEquals(
        "Test response", renderer.getLastRenderedResponse(), "Should return last response");
  }

  @Test
  void testResponseNotDuplicatedAfterReasoningFinished() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_STARTED, null, null));
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Response text", null));
    renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_FINISHED, null, null));

    // Clear output to check TURN_FINISHED doesn't render again
    output.reset();
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

    String outputStr = stripAnsi(getOutput());
    assertFalse(
        outputStr.contains("\u25cf"),
        "Should not render duplicate response dot after REASONING_FINISHED. Output: " + outputStr);
  }

  @Test
  void testToggleExpandAndCollapse() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    StringBuilder longContent = new StringBuilder();
    for (int i = 1; i <= 20; i++) {
      longContent.append("Line number ").append(i).append("\n\n");
    }
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, longContent.toString(), null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));
    assertFalse(renderer.isLastResponseExpanded(), "Should start collapsed");

    // First toggle: expand
    output.reset();
    renderer.toggleLastResponse();
    String expanded = stripAnsi(getOutput());
    assertTrue(renderer.isLastResponseExpanded(), "Should be expanded after first toggle");
    assertTrue(
        expanded.contains("Line number 20"), "Expanded should show last line. Output: " + expanded);
    assertFalse(
        expanded.contains("..."), "Expanded should not have truncation hint. Output: " + expanded);

    // Second toggle: collapse
    output.reset();
    renderer.toggleLastResponse();
    String collapsed = stripAnsi(getOutput());
    assertFalse(renderer.isLastResponseExpanded(), "Should be collapsed after second toggle");
    assertTrue(
        collapsed.contains("..."), "Collapsed should have truncation hint. Output: " + collapsed);
  }

  @Test
  void testToggleWithNoResponse() {
    renderer.toggleLastResponse();
    String outputStr = stripAnsi(getOutput());
    assertTrue(
        outputStr.contains("No response"),
        "Should show 'No response' message. Output: " + outputStr);
  }

  @Test
  void testToolCardAccumulation() {
    assertNull(renderer.getLastToolCard(), "Should be null before any tool execution");

    renderer.render(
        ObservationEvent.of(
            "s1",
            ObservationType.TOOL_STARTED,
            "run_shell_command",
            Map.of("command", "git status")));
    renderer.render(
        ObservationEvent.of(
            "s1", ObservationType.TOOL_OUTPUT_STREAM, "On branch main\nnothing to commit", null));
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, null, null));

    ToolCard card = renderer.getLastToolCard();
    assertNotNull(card, "Should have a tool card after execution");
    assertEquals("run_shell_command", card.toolName());
    assertEquals("$ git status", card.paramsDisplay());
    assertEquals(2, card.outputLines().size());
    assertEquals("On branch main", card.outputLines().get(0));
    assertEquals("nothing to commit", card.outputLines().get(1));
    assertFalse(card.isError());
  }

  @Test
  void testToolCardAccumulationError() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "read_file", Map.of()));
    renderer.render(
        ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, "Error: not found", null));

    ToolCard card = renderer.getLastToolCard();
    assertNotNull(card);
    assertEquals("read_file", card.toolName());
    assertTrue(card.isError());
    assertEquals("Error: not found", card.result());
    assertTrue(card.outputLines().isEmpty());
  }

  @Test
  void testToolCardReplacedByNewExecution() {
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "tool_a", Map.of()));
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, null, null));

    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "tool_b", Map.of()));
    renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, null, null));

    assertEquals("tool_b", renderer.getLastToolCard().toolName());
  }

  @Test
  void testPlanUpdatedDispatchesToTaskPanel() {
    TaskPanelRenderer taskPanel = new TaskPanelRenderer();
    renderer.setTaskPanel(taskPanel);

    ToDoList plan = new ToDoList(List.of(new ToDoItem("1", "Test task", TaskStatus.IN_PROGRESS)));
    renderer.render(
        ObservationEvent.of(
            "s1", ObservationType.PLAN_UPDATED, "Task added", Map.of("plan", plan)));

    assertNotNull(taskPanel.getCurrentPlan());
    assertEquals(1, taskPanel.getCurrentPlan().items().size());
  }

  @Test
  void testPlanUpdatedWithNullTaskPanelIsNoop() {
    // No taskPanel set — should not throw
    renderer.render(
        ObservationEvent.of(
            "s1", ObservationType.PLAN_UPDATED, "Task added", Map.of("plan", ToDoList.empty())));
  }

  @Test
  void testTurnStartedCallsTaskPanelOnTurnStarted() {
    TaskPanelRenderer taskPanel = new TaskPanelRenderer();
    renderer.setTaskPanel(taskPanel);

    renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
    // Just verify no exception; onTurnStarted records the time
  }

  private String getOutput() {
    terminal.writer().flush();
    return output.toString(StandardCharsets.UTF_8);
  }

  /** Strips ANSI escape sequences so tests can assert on plain text content. */
  private static String stripAnsi(String s) {
    return s.replaceAll("\033\\[[0-9;]*[A-Za-z]", "");
  }
}
