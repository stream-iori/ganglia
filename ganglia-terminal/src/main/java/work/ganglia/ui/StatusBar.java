package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;

/**
 * Renders a persistent status bar on the last line of the terminal.
 * Uses ANSI escape sequences to manage a scroll region that excludes the bottom line,
 * then writes status updates to that reserved line.
 */
public class StatusBar {
    private static final Logger logger = LoggerFactory.getLogger(StatusBar.class);

    private static final String SAVE_CURSOR = "\033[s";
    private static final String RESTORE_CURSOR = "\033[u";
    private static final String RESET_SCROLL_REGION = "\033[r";

    /** Number of rows reserved below the scroll region (gap + status line). */
    private static final int RESERVED_ROWS = 2;

    private final Terminal terminal;
    private final PrintWriter writer;
    private volatile String currentStatus = "";
    private volatile boolean enabled = false;

    public StatusBar(Terminal terminal) {
        this.terminal = terminal;
        this.writer = terminal.writer();
    }

    /**
     * Enables the status bar by setting up the scroll region and WINCH handler.
     */
    public void enable() {
        if (isDumb()) return;
        this.enabled = true;
        setupScrollRegion();
        terminal.handle(Signal.WINCH, sig -> {
            setupScrollRegion();
            refresh();
        });
        refresh();
    }

    /**
     * Disables the status bar, restoring the full terminal scroll region.
     */
    public void disable() {
        if (!enabled) return;
        this.enabled = false;
        writer.print(RESET_SCROLL_REGION);
        int rows = getRows();
        // Clear the reserved rows (gap line + status line)
        for (int r = rows - RESERVED_ROWS + 1; r <= rows; r++) {
            writer.print(String.format("\033[%d;1H\033[2K", r));
        }
        writer.flush();
    }

    public void setThinking() {
        update("\u23f3 Thinking...");
    }

    public void setExecutingTool(String toolName) {
        update("\u2699 Executing: " + toolName);
    }

    public void setIdle() {
        update("\u2713 Ready");
    }

    public void clear() {
        update("");
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    private void update(String status) {
        this.currentStatus = status;
        refresh();
    }

    private void refresh() {
        if (!enabled || isDumb()) return;
        int rows = getRows();
        int cols = getCols();
        String display = currentStatus.length() > cols ? currentStatus.substring(0, cols) : currentStatus;

        writer.print(SAVE_CURSOR);

        // Row rows-1: dim separator line
        int sepRow = rows - RESERVED_ROWS + 1;
        writer.print(String.format("\033[%d;1H\033[2K", sepRow));
        writer.print("\033[2m"); // dim
        String sep = "\u2500".repeat(Math.min(cols, 60));
        writer.print(sep);
        writer.print("\033[0m");

        // Row rows: status bar (reverse video)
        writer.print(String.format("\033[%d;1H\033[2K", rows));
        writer.print("\033[7m"); // reverse video
        writer.print(display);
        int padding = cols - displayWidth(display);
        if (padding > 0) {
            writer.print(" ".repeat(padding));
        }
        writer.print("\033[0m"); // reset

        writer.print(RESTORE_CURSOR);
        writer.flush();
    }

    private void setupScrollRegion() {
        if (isDumb()) return;
        int rows = getRows();
        if (rows < RESERVED_ROWS + 2) return;
        // Set scroll region to exclude reserved bottom rows (gap + status)
        writer.print(String.format("\033[1;%dr", rows - RESERVED_ROWS));
        // Move cursor to bottom of scroll region
        writer.print(String.format("\033[%d;1H", rows - RESERVED_ROWS));
        writer.flush();
    }

    private int getRows() {
        int rows = terminal.getHeight();
        return rows > 0 ? rows : 24;
    }

    private int getCols() {
        int cols = terminal.getWidth();
        return cols > 0 ? cols : 80;
    }

    private boolean isDumb() {
        return "dumb".equals(terminal.getType());
    }

    /**
     * Approximate display width — counts characters naively.
     * Good enough for ASCII + simple Unicode; CJK would need ICU4J.
     */
    private int displayWidth(String s) {
        return s.codePointCount(0, s.length());
    }
}
