package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import work.ganglia.kernel.todo.TaskStatus;
import work.ganglia.kernel.todo.ToDoItem;
import work.ganglia.kernel.todo.ToDoList;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StatusBarTest {

    private Terminal terminal;
    private ByteArrayOutputStream output;
    private StatusBar statusBar;

    @BeforeEach
    void setUp() throws IOException {
        output = new ByteArrayOutputStream();
        terminal = TerminalBuilder.builder()
                .dumb(true)
                .streams(System.in, output)
                .build();
        statusBar = new StatusBar(terminal);
    }

    @AfterEach
    void tearDown() throws IOException {
        terminal.close();
    }

    @Test
    void testSetThinking() {
        statusBar.setThinking();
        assertEquals("\u23f3 Thinking...", statusBar.getCurrentStatus());
    }

    @Test
    void testSetExecutingTool() {
        statusBar.setExecutingTool("run_shell_command");
        assertEquals("\u2699 Executing: run_shell_command", statusBar.getCurrentStatus());
    }

    @Test
    void testSetIdle() {
        statusBar.setIdle();
        assertEquals("\u2713 Ready", statusBar.getCurrentStatus());
    }

    @Test
    void testClear() {
        statusBar.setThinking();
        statusBar.clear();
        assertEquals("", statusBar.getCurrentStatus());
    }

    @Test
    void testEnableDisableOnDumbTerminal() {
        // Dumb terminal — enable/disable should be safe no-ops
        statusBar.enable();
        statusBar.setIdle();
        statusBar.disable();
        // Just verify no exceptions thrown
    }

    @Test
    void testDefaultReservedRows() {
        int expected = StatusBar.INPUT_BOX_HEIGHT + StatusBar.STATUS_BAR_HEIGHT + StatusBar.BOTTOM_PADDING;
        assertEquals(expected, statusBar.getReservedRows());
    }

    @Test
    void testRecalculateLayoutWithNoTaskPanel() {
        int base = StatusBar.INPUT_BOX_HEIGHT + StatusBar.STATUS_BAR_HEIGHT + StatusBar.BOTTOM_PADDING;
        statusBar.recalculateLayout();
        assertEquals(base, statusBar.getReservedRows(), "Should remain base with no task panel");
    }

    @Test
    void testRecalculateLayoutWithEmptyTaskPanel() {
        int base = StatusBar.INPUT_BOX_HEIGHT + StatusBar.STATUS_BAR_HEIGHT + StatusBar.BOTTOM_PADDING;
        TaskPanelRenderer taskPanel = new TaskPanelRenderer();
        statusBar.setTaskPanel(taskPanel);
        statusBar.recalculateLayout();
        assertEquals(base, statusBar.getReservedRows(), "Should remain base with empty task panel");
    }

    @Test
    void testRecalculateLayoutWithTasks() {
        int base = StatusBar.INPUT_BOX_HEIGHT + StatusBar.STATUS_BAR_HEIGHT + StatusBar.BOTTOM_PADDING;
        TaskPanelRenderer taskPanel = new TaskPanelRenderer();
        taskPanel.updatePlan(new ToDoList(List.of(
                new ToDoItem("1", "Task one", TaskStatus.TODO),
                new ToDoItem("2", "Task two", TaskStatus.IN_PROGRESS)
        )));
        statusBar.setTaskPanel(taskPanel);
        statusBar.recalculateLayout();
        // taskHeight(header + 2 items = 3) + base
        assertEquals(base + 3, statusBar.getReservedRows());
    }

    @Test
    void testGetInputRow() {
        // Below input: bottom border (1) + status + padding
        int h = terminal.getHeight() > 0 ? terminal.getHeight() : 24;
        assertEquals(h - StatusBar.BOTTOM_PADDING - StatusBar.STATUS_BAR_HEIGHT - 1, statusBar.getInputRow());
    }

    @Test
    void testGetScrollBottom() {
        // scroll bottom = H - reservedRows
        int h = terminal.getHeight() > 0 ? terminal.getHeight() : 24;
        int base = StatusBar.INPUT_BOX_HEIGHT + StatusBar.STATUS_BAR_HEIGHT + StatusBar.BOTTOM_PADDING;
        assertEquals(h - base, statusBar.getScrollBottom());
    }
}
