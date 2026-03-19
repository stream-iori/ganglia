package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
}
