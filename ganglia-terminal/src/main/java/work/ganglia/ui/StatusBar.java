package work.ganglia.ui;

import java.io.PrintWriter;

import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a persistent bottom panel on the terminal. Uses ANSI escape sequences to manage a scroll
 * region that excludes the bottom rows.
 *
 * <p>Layout (bottom-up from row H, where P = BOTTOM_PADDING):
 *
 * <pre>
 *   Rows H..H-(P-1):  (empty padding — keeps UI off the terminal edge)
 *   Row  H-P:         status text (dim, no background)
 *   Row  H-P-1:       ─── input bottom border
 *   Row  H-P-2:       ❯ cursor (JLine prompt renders here)
 *   Row  H-P-3:       ─── input top border
 *   Rows above:       task panel (if any)
 * </pre>
 *
 * <p>The scroll region is rows 1..(H - reservedRows). JLine's readLine() is called with the cursor
 * positioned at the input row inside the reserved area.
 */
public class StatusBar {
  private static final Logger logger = LoggerFactory.getLogger(StatusBar.class);

  private static final String DIM_ON = "\033[2m";
  private static final String STYLE_RESET = "\033[0m";

  // ── Layout building blocks ──────────────────────────────────────────
  // All row positions are derived from these constants.
  // To adjust spacing, change only these values — everything else follows.

  /** Input box: top border (1) + input line (1) + bottom border (1). */
  static final int INPUT_BOX_HEIGHT = 3;

  /** Status text: single row below the input box. */
  static final int STATUS_BAR_HEIGHT = 1;

  /** Empty rows at the very bottom to lift the UI off the terminal edge. */
  static final int BOTTOM_PADDING = 3;

  /** Rows below the input line: bottom border (1) + status + padding. */
  private static final int ROWS_BELOW_INPUT = 1 + STATUS_BAR_HEIGHT + BOTTOM_PADDING;

  /** Column where the cursor parks in the input row (after the " ❯ " prompt). */
  private static final int INPUT_CURSOR_COL = 4;

  /** Extra rows to clear above reserved area during resize to catch reflow artifacts. */
  private static final int RESIZE_MARGIN = 4;

  /** Minimum scroll region height for the full layout to be usable. */
  private static final int MIN_SCROLL_HEIGHT = 2;

  /** Base reserved rows (no task panel). */
  private static final int BASE_RESERVED = INPUT_BOX_HEIGHT + STATUS_BAR_HEIGHT + BOTTOM_PADDING;

  // ── Instance state ──────────────────────────────────────────────────

  private final Terminal terminal;
  private final PrintWriter writer;

  /**
   * Shared lock for all terminal write sequences. Any code that emits multi-call ANSI sequences
   * (saveCursor → writes → restoreCursor) must synchronize on this lock to prevent interleaving
   * from concurrent threads.
   */
  public final Object terminalWriteLock = new Object();

  private volatile String currentStatus = "";
  private volatile boolean enabled = false;
  private volatile int reservedRows = BASE_RESERVED;
  private TaskPanelRenderer taskPanel;

  public StatusBar(Terminal terminal) {
    this.terminal = terminal;
    this.writer = terminal.writer();
  }

  public void setTaskPanel(TaskPanelRenderer taskPanel) {
    this.taskPanel = taskPanel;
  }

  // ── Layout queries (used by other renderers) ────────────────────────

  /** Returns the total number of rows reserved below the scroll region. */
  public int getReservedRows() {
    return reservedRows;
  }

  /** Returns the 1-based row where JLine renders the input prompt. */
  public int getInputRow() {
    return getRows() - ROWS_BELOW_INPUT;
  }

  /** Returns the 1-based row of the last line in the scroll region. */
  public int getScrollBottom() {
    return getRows() - reservedRows;
  }

  /**
   * Parks the cursor at the input row. Call after writing to the scroll region so the cursor stays
   * visible and anchored in the input box. Must be called while holding {@link #terminalWriteLock}.
   */
  public void parkCursorAtInput() {
    if (!enabled || isDumb()) {
      return;
    }
    writer.print(AnsiCodes.moveTo(getInputRow(), INPUT_CURSOR_COL));
  }

  // ── Layout lifecycle ────────────────────────────────────────────────

  /**
   * Recalculates layout based on current task panel height. Re-establishes scroll region and
   * refreshes if enabled.
   */
  public void recalculateLayout() {
    int taskHeight = (taskPanel != null) ? taskPanel.getHeight(getRows()) : 0;
    reservedRows = BASE_RESERVED + taskHeight;
    if (enabled) {
      setupScrollRegion();
      refresh();
    }
  }

  /** Enables the status bar: sets up scroll region, WINCH handler, and initial paint. */
  public void enable() {
    if (isDumb()) {
      return;
    }
    this.enabled = true;
    setupScrollRegion();
    terminal.handle(Signal.WINCH, sig -> handleResize());
    refresh();
  }

  /** Disables the status bar, restoring the full terminal scroll region. */
  public void disable() {
    if (!enabled) {
      return;
    }
    this.enabled = false;
    writer.print(AnsiCodes.resetScrollRegion());
    clearRows(getRows() - reservedRows + 1, reservedRows);
    writer.flush();
  }

  // ── Status updates ──────────────────────────────────────────────────

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

  // ── Rendering ───────────────────────────────────────────────────────

  void refresh() {
    if (!enabled || isDumb()) {
      return;
    }

    synchronized (terminalWriteLock) {
      int rows = getRows();
      int cols = getCols();

      if (rows < reservedRows + MIN_SCROLL_HEIGHT) {
        renderDegradedStatus(rows, cols);
        return;
      }

      writer.print(AnsiCodes.saveCursor());

      int row = rows - reservedRows + 1;
      row = renderTaskPanel(row, rows, cols);
      row = renderInputBox(row, cols);
      row = renderStatusText(row, cols);
      renderPadding(row);

      writer.print(AnsiCodes.restoreCursor());
      writer.flush();
    }
  }

  /** Renders the task panel if present. Returns the next row to render at. */
  private int renderTaskPanel(int startRow, int termRows, int cols) {
    if (taskPanel == null) {
      return startRow;
    }
    int height = taskPanel.getHeight(termRows);
    if (height <= 0) {
      return startRow;
    }
    taskPanel.renderAt(writer, startRow, cols, height);
    return startRow + height;
  }

  /** Renders top border, input line (cleared for JLine), bottom border. Returns next row. */
  private int renderInputBox(int startRow, int cols) {
    renderHorizontalRule(startRow, cols);
    clearLine(startRow + 1); // input line — JLine draws the prompt here
    renderHorizontalRule(startRow + 2, cols);
    return startRow + INPUT_BOX_HEIGHT;
  }

  /** Renders the status text in dim style. Returns next row. */
  private int renderStatusText(int startRow, int cols) {
    String display = truncate(currentStatus, cols);
    clearLine(startRow);
    writer.print(DIM_ON);
    writer.print("  " + display);
    writer.print(STYLE_RESET);
    return startRow + STATUS_BAR_HEIGHT;
  }

  /** Renders empty padding rows at the bottom. */
  private void renderPadding(int startRow) {
    clearRows(startRow, BOTTOM_PADDING);
  }

  /** Graceful degradation: single dim status line on the last row. */
  private void renderDegradedStatus(int rows, int cols) {
    String display = truncate(currentStatus, cols);
    clearLine(rows);
    writer.print(DIM_ON + display + STYLE_RESET);
    writer.flush();
  }

  // ── ANSI primitives ─────────────────────────────────────────────────

  /** Draws a dim horizontal rule (─) spanning the full terminal width at the given row. */
  private void renderHorizontalRule(int row, int cols) {
    clearLine(row);
    writer.print(DIM_ON);
    writer.print("\u2500".repeat(cols));
    writer.print(STYLE_RESET);
  }

  /** Moves to the given row and clears it. */
  private void clearLine(int row) {
    writer.print(AnsiCodes.moveAndClear(row));
  }

  /** Clears {@code count} consecutive rows starting at {@code startRow}. */
  private void clearRows(int startRow, int count) {
    for (int i = 0; i < count; i++) {
      clearLine(startRow + i);
    }
  }

  private String truncate(String text, int maxWidth) {
    return text.length() > maxWidth ? text.substring(0, maxWidth) : text;
  }

  // ── Scroll region management ────────────────────────────────────────

  private void setupScrollRegion() {
    if (isDumb()) {
      return;
    }
    int rows = getRows();
    if (rows < reservedRows + MIN_SCROLL_HEIGHT) {
      return;
    }
    int scrollBottom = rows - reservedRows;
    writer.print(AnsiCodes.setScrollRegion(1, scrollBottom));
    writer.print(AnsiCodes.moveTo(scrollBottom, 1));
    writer.flush();
  }

  private void handleResize() {
    if (!enabled || isDumb()) {
      return;
    }
    synchronized (terminalWriteLock) {
      int rows = getRows();
      writer.print(AnsiCodes.resetScrollRegion());

      int taskHeight = (taskPanel != null) ? taskPanel.getHeight(rows) : 0;
      reservedRows = BASE_RESERVED + taskHeight;

      // Clear from a few rows above the reserved area to catch reflow artifacts
      int firstReserved = rows - reservedRows + 1;
      int safeStart = Math.max(1, firstReserved - RESIZE_MARGIN);
      writer.print(AnsiCodes.moveTo(safeStart, 1) + "\033[J");

      setupScrollRegion();
      refresh();
    }
  }

  // ── Terminal queries ────────────────────────────────────────────────

  private void update(String status) {
    this.currentStatus = status;
    refresh();
  }

  private int getRows() {
    int rows = terminal.getHeight();
    return rows > 0 ? rows : 24;
  }

  int getCols() {
    int cols = terminal.getWidth();
    return cols > 0 ? cols : 80;
  }

  private boolean isDumb() {
    return "dumb".equals(terminal.getType());
  }

  int displayWidth(String s) {
    int width = 0;
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      int w = org.jline.utils.WCWidth.wcwidth(cp);
      width += (w > 0) ? w : 1;
      i += Character.charCount(cp);
    }
    return width;
  }
}
