package work.ganglia.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

/**
 * Full-terminal floating overlay viewer for tool execution details. Covers rows 1..height with a
 * bordered box, leaving scroll region state intact. Supports scrolling with arrow keys, PgUp/PgDn,
 * g/G. Press q or ESC to return to the main screen.
 *
 * <p>On entry: saves cursor, hides cursor, draws overlay over entire terminal.<br>
 * On exit: clears each overlay row, restores cursor, shows cursor, calls {@link
 * StatusBar#refresh()} to redraw the bottom panel.
 */
public class DetailView {

  private static final int HORIZONTAL_MARGIN = 2;

  private final Terminal terminal;
  private final PrintWriter writer;
  private final StatusBar statusBar;

  public DetailView(Terminal terminal, StatusBar statusBar) {
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.statusBar = statusBar;
  }

  /**
   * Shows the tool card detail as a full-terminal floating overlay. Blocks until the user presses q
   * or ESC.
   */
  public void show(ToolCard card) {
    List<String> contentLines = buildContentLines(card);
    Attributes prev = terminal.enterRawMode();

    try {
      writer.print(AnsiCodes.saveCursor());
      writer.print(AnsiCodes.hideCursor());
      writer.flush();

      int scrollOffset = 0;
      boolean running = true;

      render(card, contentLines, scrollOffset);

      while (running) {
        int rows = Math.max(terminal.getHeight(), 5);
        int viewportRows = rows - 2; // title bar + bottom bar
        int maxScroll = Math.max(0, contentLines.size() - viewportRows);

        int c;
        try {
          c = terminal.reader().read();
        } catch (IOException e) {
          break;
        }
        if (c == -1) {
          break;
        }

        switch (c) {
          case 'q', 'Q' -> running = false;
          case 'j' -> scrollOffset = Math.min(maxScroll, scrollOffset + 1);
          case 'k' -> scrollOffset = Math.max(0, scrollOffset - 1);
          case 'g' -> scrollOffset = 0;
          case 'G' -> scrollOffset = maxScroll;
          case '\033' -> { // ESC or escape sequence
            int next = readWithTimeout(50);
            if (next == -1 || next == '\033') {
              running = false;
            } else if (next == '[') {
              int code = readChar();
              switch (code) {
                case 'A' -> scrollOffset = Math.max(0, scrollOffset - 1);
                case 'B' -> scrollOffset = Math.min(maxScroll, scrollOffset + 1);
                case '5' -> {
                  readChar();
                  scrollOffset = Math.max(0, scrollOffset - viewportRows);
                }
                case '6' -> {
                  readChar();
                  scrollOffset = Math.min(maxScroll, scrollOffset + viewportRows);
                }
                case 'H' -> scrollOffset = 0;
                case 'F' -> scrollOffset = maxScroll;
                default -> {}
              }
            }
          }
          default -> {}
        }

        if (running) {
          render(card, contentLines, scrollOffset);
        }
      }
    } finally {
      clearOverlay();
      writer.print(AnsiCodes.restoreCursor());
      writer.print(AnsiCodes.showCursor());
      writer.flush();
      terminal.setAttributes(prev);
      // Scroll region was never touched — just redraw the bottom panel.
      statusBar.refresh();
    }
  }

  /** Builds the content lines displayed in the scrollable area. Package-private for testing. */
  List<String> buildContentLines(ToolCard card) {
    List<String> lines = new ArrayList<>();

    if (card.paramsDisplay() != null && !card.paramsDisplay().isEmpty()) {
      lines.add(card.paramsDisplay());
      lines.add("");
    }

    if (card.outputLines() != null) {
      lines.addAll(card.outputLines());
    }

    if (card.result() != null && !card.result().isEmpty()) {
      lines.add("");
      lines.add(card.result());
    }

    return lines;
  }

  private void render(ToolCard card, List<String> contentLines, int scrollOffset) {
    int rows = Math.max(terminal.getHeight(), 5);
    int cols = Math.max(terminal.getWidth(), 40);
    int innerWidth = overlayInnerWidth(cols);
    int viewportRows = rows - 2; // title bar + bottom bar

    // Title bar (row 1, reverse video)
    String status = card.isError() ? "\u2717" : "\u2713";
    String duration = AnsiCodes.formatDuration(card.durationMs());
    String title = " " + card.toolName() + "  " + status + " " + duration + "  [q] exit ";
    writer.print(AnsiCodes.moveTo(1, 1) + AnsiCodes.clearLine() + "\033[7m");
    writer.print(padRight(title, cols));
    writer.print("\033[0m");

    // Content area (rows 2..rows-1)
    for (int i = 0; i < viewportRows; i++) {
      int row = i + 2;
      writer.print(AnsiCodes.moveTo(row, 1) + AnsiCodes.clearLine());
      int lineIdx = scrollOffset + i;
      if (lineIdx < contentLines.size()) {
        String line = contentLines.get(lineIdx);
        // Truncate to overlay inner width and append ellipsis if needed
        String plain = stripAnsi(line);
        if (plain.length() > innerWidth) {
          line = line.substring(0, innerWidth) + "\u2026";
        }
        writer.print(line);
      }
    }

    // Bottom bar (last row, reverse video)
    String leftHelp = " \u2191\u2193 scroll  PgUp/PgDn page  q back";
    String rightInfo = "[" + (scrollOffset + 1) + "/" + contentLines.size() + "] ";
    int gap = cols - leftHelp.length() - rightInfo.length();
    String bottomBar = leftHelp + (gap > 0 ? " ".repeat(gap) : " ") + rightInfo;
    writer.print(AnsiCodes.moveTo(rows, 1) + AnsiCodes.clearLine() + "\033[7m");
    writer.print(bottomBar.length() > cols ? bottomBar.substring(0, cols) : bottomBar);
    writer.print("\033[0m");

    writer.flush();
  }

  private void clearOverlay() {
    int rows = Math.max(terminal.getHeight(), 5);
    for (int row = 1; row <= rows; row++) {
      writer.print(AnsiCodes.moveTo(row, 1) + AnsiCodes.clearLine());
    }
  }

  private int overlayInnerWidth(int cols) {
    return Math.max(cols - HORIZONTAL_MARGIN * 2 - 2, 10);
  }

  private static String stripAnsi(String s) {
    return s.replaceAll("\033\\[[0-9;]*[A-Za-z]", "");
  }

  private int readChar() {
    try {
      return terminal.reader().read();
    } catch (IOException e) {
      return -1;
    }
  }

  private int readWithTimeout(int timeoutMs) {
    try {
      long deadline = System.currentTimeMillis() + timeoutMs;
      while (System.currentTimeMillis() < deadline) {
        if (terminal.reader().ready()) {
          return terminal.reader().read();
        }
        Thread.sleep(5);
      }
      return -1;
    } catch (Exception e) {
      return -1;
    }
  }

  private String padRight(String s, int width) {
    if (s.length() >= width) {
      return s.substring(0, width);
    }
    return s + " ".repeat(width - s.length());
  }
}
