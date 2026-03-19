package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class EventRendererTest {

    private Terminal terminal;
    private ByteArrayOutputStream output;
    private EventRenderer renderer;
    private StatusBar statusBar;

    @BeforeEach
    void setUp() throws IOException {
        output = new ByteArrayOutputStream();
        terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(System.in, output)
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

        String outputStr = getOutput();
        assertTrue(outputStr.contains("Generating..."), "Should show progress indicator");
    }

    @Test
    void testToolCardRendering() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "run_shell_command",
                Map.of("command", "git status")));
        String outputStr = getOutput();

        assertTrue(outputStr.contains("\u256d"), "Should contain top-left corner");
        assertTrue(outputStr.contains("run_shell_command"), "Should contain tool name");
        assertTrue(outputStr.contains("$ git status"), "Should contain command with $ prefix");
    }

    @Test
    void testToolFinishedClosesCard() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "read_file", Map.of()));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, "ok", null));

        String outputStr = getOutput();
        assertTrue(outputStr.contains("\u2570"), "Should contain bottom-left corner");
        assertTrue(outputStr.contains("\u2713"), "Should contain checkmark for success");
    }

    @Test
    void testToolFinishedError() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "run_shell_command", Map.of()));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_FINISHED, "Error: command failed", null));

        String outputStr = getOutput();
        assertTrue(outputStr.contains("\u2717"), "Should contain X mark for failure");
    }

    @Test
    void testToolOutputStream() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_STARTED, "run_shell_command",
                Map.of("command", "ls")));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOOL_OUTPUT_STREAM, "file1.txt\nfile2.txt", null));

        String outputStr = getOutput();
        assertTrue(outputStr.contains("\u2502"), "Should contain vertical bar for card border");
        assertTrue(outputStr.contains("file1.txt"));
        assertTrue(outputStr.contains("file2.txt"));
    }

    @Test
    void testReasoningStartedUpdatesStatusBar() {
        renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_STARTED, null, null));
        assertEquals("\u23f3 Thinking...", statusBar.getCurrentStatus());
    }

    @Test
    void testTurnFinishedSetsIdle() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));
        assertEquals("\u2713 Ready", statusBar.getCurrentStatus());
    }

    @Test
    void testErrorRendering() {
        renderer.render(ObservationEvent.of("s1", ObservationType.ERROR, "Something went wrong", null));
        String outputStr = getOutput();
        assertTrue(outputStr.contains("Something went wrong"));
    }

    @Test
    void testTurnFinishedRendersResponseWithDot() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Hello there!", null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

        String outputStr = getOutput();
        // Response dot prefix: ●
        assertTrue(outputStr.contains("\u25cf"), "Should contain green dot ● for response");
        assertTrue(outputStr.contains("Hello there"), "Should contain response content");
    }

    @Test
    void testResponseTruncation() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
        StringBuilder longContent = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            longContent.append("Line number ").append(i).append("\n\n");
        }
        renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, longContent.toString(), null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

        String outputStr = getOutput();
        assertTrue(outputStr.contains("..."), "Should contain truncation ellipsis");
        assertTrue(outputStr.contains("lines"), "Should mention remaining lines count");
        assertTrue(outputStr.contains("Ctrl+O"), "Should mention Ctrl+O to expand");
    }

    @Test
    void testGetLastRenderedResponse() {
        assertNull(renderer.getLastRenderedResponse(), "Should be null before any turn");

        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Test response", null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

        assertEquals("Test response", renderer.getLastRenderedResponse());
    }

    @Test
    void testResponseNotDuplicatedAfterReasoningFinished() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
        renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_STARTED, null, null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, "Response text", null));
        renderer.render(ObservationEvent.of("s1", ObservationType.REASONING_FINISHED, null, null));

        // Clear output to check TURN_FINISHED doesn't render again
        output.reset();
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));

        String outputStr = getOutput();
        assertFalse(outputStr.contains("\u25cf"), "Should not render duplicate response dot after REASONING_FINISHED");
    }

    @Test
    void testToggleExpandAndCollapse() {
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_STARTED, null, null));
        StringBuilder longContent = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            longContent.append("Line number ").append(i).append("\n\n");
        }
        renderer.render(ObservationEvent.of("s1", ObservationType.TOKEN_RECEIVED, longContent.toString(), null));
        renderer.render(ObservationEvent.of("s1", ObservationType.TURN_FINISHED, null, null));
        assertFalse(renderer.isLastResponseExpanded(), "Should start collapsed");

        // First toggle: expand
        output.reset();
        renderer.toggleLastResponse();
        String expanded = getOutput();
        assertTrue(renderer.isLastResponseExpanded(), "Should be expanded after first toggle");
        assertTrue(expanded.contains("Line number 20"), "Expanded should show last line");
        assertFalse(expanded.contains("..."), "Expanded should not have truncation hint");

        // Second toggle: collapse
        output.reset();
        renderer.toggleLastResponse();
        String collapsed = getOutput();
        assertFalse(renderer.isLastResponseExpanded(), "Should be collapsed after second toggle");
        assertTrue(collapsed.contains("..."), "Collapsed should have truncation hint");
    }

    @Test
    void testToggleWithNoResponse() {
        renderer.toggleLastResponse();
        String outputStr = getOutput();
        assertTrue(outputStr.contains("No response"), "Should show 'No response' message");
    }

    private String getOutput() {
        terminal.writer().flush();
        return output.toString();
    }
}
