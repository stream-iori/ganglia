package work.ganglia.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

/**
 * Full-screen alternate-buffer viewer for tool execution details. Supports scrolling with arrow
 * keys, PgUp/PgDn, Home/End. Press q or ESC to return to the main screen.
 */
public class DetailView {

  private final Terminal terminal;
  private final PrintWriter writer;

  public DetailView(Terminal terminal) {
    this.terminal = terminal;
    this.writer = terminal.writer();
  }

  /**
   * Shows the tool card detail in an alternate screen buffer. Blocks until the user presses q or
   * ESC.
   */
  public void show(ToolCard card) {
    List<String> contentLines = buildContentLines(card);
    Attributes prev = terminal.enterRawMode();

    try {
      writer.print("\033[?1049h"); // enter alternate screen
      writer.print("\033[?25l"); // hide cursor
      writer.flush();

      int scrollOffset = 0;
      boolean running = true;

      render(card, contentLines, scrollOffset);

      while (running) {
        int rows = Math.max(terminal.getHeight(), 5);
        int viewportRows = rows - 2;
        int maxScroll = Math.max(0, contentLines.size() - viewportRows);

        int c;
        try {
          c = terminal.reader().read();
        } catch (IOException e) {
          break;
        }
        if (c == -1) break;

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
      writer.print("\033[?25h"); // show cursor
      writer.print("\033[?1049l"); // leave alternate screen
      writer.flush();
      terminal.setAttributes(prev);
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
    int viewportRows = rows - 2;

    // Title bar (reverse video)
    String status = card.isError() ? "\u2717" : "\u2713";
    String duration = formatDuration(card.durationMs());
    String title = " " + card.toolName() + "  " + status + " " + duration + " ";
    writer.print("\033[1;1H\033[2K\033[7m");
    writer.print(padRight(title, cols));
    writer.print("\033[0m");

    // Content area
    for (int i = 0; i < viewportRows; i++) {
      writer.print("\033[" + (i + 2) + ";1H\033[2K");
      int lineIdx = scrollOffset + i;
      if (lineIdx < contentLines.size()) {
        String line = contentLines.get(lineIdx);
        if (line.length() > cols) {
          line = line.substring(0, cols);
        }
        writer.print(line);
      }
    }

    // Bottom bar (reverse video)
    String leftHelp = " \u2191\u2193 scroll  PgUp/PgDn page  q back";
    String rightInfo = "[" + (scrollOffset + 1) + "/" + contentLines.size() + "] ";
    int gap = cols - leftHelp.length() - rightInfo.length();
    String bottomBar = leftHelp + (gap > 0 ? " ".repeat(gap) : " ") + rightInfo;
    writer.print("\033[" + rows + ";1H\033[2K\033[7m");
    writer.print(bottomBar.length() > cols ? bottomBar.substring(0, cols) : bottomBar);
    writer.print("\033[0m");

    writer.flush();
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
      // Use non-blocking peek approach
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
    if (s.length() >= width) return s.substring(0, width);
    return s + " ".repeat(width - s.length());
  }

  private String formatDuration(long millis) {
    if (millis < 1000) return millis + "ms";
    double secs = millis / 1000.0;
    return String.format("%.1fs", secs);
  }
}
