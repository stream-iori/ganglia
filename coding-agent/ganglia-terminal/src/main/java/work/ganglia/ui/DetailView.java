package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlocking;
import org.jline.utils.NonBlockingReader;

/**
 * Full-terminal overlay viewer for tool cards and response text.
 *
 * <p>Uses the <em>alternate screen buffer</em> ({@code \033[?1049h} / {@code \033[?1049l}) so the
 * main screen — scroll region, conversation history, status bar — is fully preserved and
 * automatically restored when the overlay closes. No manual screen-save/restore is needed.
 *
 * <p>Input is read via JLine's {@link NonBlockingReader}, which correctly handles multi-byte escape
 * sequences (arrow keys) with a real timeout rather than polling {@code ready()}.
 *
 * <p>Supports: j/k or ↑/↓ (line scroll), g/G or Home/End (top/bottom), PgUp/PgDn (page), q or ESC
 * to exit.
 */
public class DetailView {

  /** Timeout in milliseconds when reading the rest of an escape sequence after the ESC byte. */
  private static final int ESC_TIMEOUT_MS = 50;

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
   * Shows arbitrary text content as a full-terminal overlay. Blocks until the user presses q or
   * ESC.
   */
  public void show(String title, String content) {
    showOverlay(title, buildContentLines(content));
  }

  /**
   * Shows the tool card detail as a full-terminal overlay. Blocks until the user presses q or ESC.
   */
  public void show(ToolCard card) {
    String status = card.isError() ? "\u2717" : "\u2713";
    String duration = AnsiCodes.formatDuration(card.durationMs());
    showOverlay(card.toolName() + "  " + status + " " + duration, buildContentLines(card));
  }

  // ── Content builders (package-private for tests) ────────────────────

  /**
   * Splits a plain text string on newlines into display lines. Returns an empty list for null/empty
   * input.
   */
  List<String> buildContentLines(String content) {
    if (content == null || content.isEmpty()) {
      return List.of();
    }
    return List.of(content.split("\n", -1));
  }

  /** Builds display lines from a tool card (params → output → result). */
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

  // ── Overlay lifecycle ────────────────────────────────────────────────

  private void showOverlay(String title, List<String> contentLines) {
    if (isDumb()) {
      // Dumb terminals don't support alternate screen — print inline and return.
      printInline(title, contentLines);
      return;
    }

    Attributes prev = terminal.enterRawMode();
    NonBlockingReader nbReader = NonBlocking.nonBlocking("detail-view", terminal.reader());

    try {
      // Enter alternate screen: main screen is fully preserved.
      writer.print(AnsiCodes.enterAltScreen());
      writer.print(AnsiCodes.hideCursor());
      writer.flush();

      int scrollOffset = 0;
      boolean running = true;
      renderOverlay(title, contentLines, scrollOffset);

      while (running) {
        int rows = Math.max(terminal.getHeight(), 5);
        int viewportRows = rows - 2;
        int maxScroll = Math.max(0, contentLines.size() - viewportRows);

        int c = readChar(nbReader);
        if (c == NonBlockingReader.EOF) {
          break;
        }

        switch (c) {
          case 'q', 'Q' -> running = false;
          case 'j' -> scrollOffset = Math.min(maxScroll, scrollOffset + 1);
          case 'k' -> scrollOffset = Math.max(0, scrollOffset - 1);
          case 'g' -> scrollOffset = 0;
          case 'G' -> scrollOffset = maxScroll;
          case '\033' -> {
            int next = readCharTimeout(nbReader, ESC_TIMEOUT_MS);
            if (next == NonBlockingReader.EOF || next == NonBlockingReader.READ_EXPIRED) {
              // Bare ESC — exit overlay
              running = false;
            } else if (next == '[') {
              int code = readChar(nbReader);
              switch (code) {
                case 'A' -> scrollOffset = Math.max(0, scrollOffset - 1); // up arrow
                case 'B' -> scrollOffset = Math.min(maxScroll, scrollOffset + 1); // down arrow
                case 'H' -> scrollOffset = 0; // Home
                case 'F' -> scrollOffset = maxScroll; // End
                case '5' -> { // PgUp — consume trailing '~'
                  readChar(nbReader);
                  scrollOffset = Math.max(0, scrollOffset - viewportRows);
                }
                case '6' -> { // PgDn — consume trailing '~'
                  readChar(nbReader);
                  scrollOffset = Math.min(maxScroll, scrollOffset + viewportRows);
                }
                default -> {}
              }
            }
          }
          default -> {}
        }

        if (running) {
          renderOverlay(title, contentLines, scrollOffset);
        }
      }
    } finally {
      // Exit alternate screen: main screen is restored exactly as we left it.
      writer.print(AnsiCodes.exitAltScreen());
      writer.print(AnsiCodes.showCursor());
      writer.flush();
      terminal.setAttributes(prev);
    }
  }

  // ── Rendering ────────────────────────────────────────────────────────

  private void renderOverlay(String title, List<String> contentLines, int scrollOffset) {
    int rows = Math.max(terminal.getHeight(), 5);
    int cols = Math.max(terminal.getWidth(), 40);
    int innerWidth = overlayInnerWidth(cols);
    int viewportRows = rows - 2;

    // Title bar — row 1, reverse video
    writer.print(AnsiCodes.moveTo(1, 1) + AnsiCodes.clearLine() + "\033[7m");
    writer.print(padRight(" " + title + "  [q] exit ", cols));
    writer.print("\033[0m");

    // Content rows 2..rows-1
    for (int i = 0; i < viewportRows; i++) {
      writer.print(AnsiCodes.moveTo(i + 2, 1) + AnsiCodes.clearLine());
      int lineIdx = scrollOffset + i;
      if (lineIdx < contentLines.size()) {
        String line = contentLines.get(lineIdx);
        if (stripAnsi(line).length() > innerWidth) {
          line = line.substring(0, innerWidth) + "\u2026";
        }
        writer.print(line);
      }
    }

    // Bottom bar — last row, reverse video
    String leftHelp = " \u2191\u2193/jk scroll  PgUp/PgDn page  g/G top/bottom  q back";
    String rightInfo = "[" + (scrollOffset + 1) + "/" + contentLines.size() + "] ";
    int gap = cols - leftHelp.length() - rightInfo.length();
    String bottomBar = leftHelp + (gap > 0 ? " ".repeat(gap) : " ") + rightInfo;
    writer.print(AnsiCodes.moveTo(rows, 1) + AnsiCodes.clearLine() + "\033[7m");
    writer.print(bottomBar.length() > cols ? bottomBar.substring(0, cols) : bottomBar);
    writer.print("\033[0m");

    writer.flush();
  }

  /** Fallback for dumb terminals: print content inline without any overlay mechanics. */
  private void printInline(String title, List<String> contentLines) {
    writer.println("=== " + title + " ===");
    for (String line : contentLines) {
      writer.println(line);
    }
    writer.println("=== end ===");
    writer.flush();
  }

  // ── Helpers ──────────────────────────────────────────────────────────

  private int readChar(NonBlockingReader nbReader) {
    try {
      return nbReader.read();
    } catch (Exception e) {
      return NonBlockingReader.EOF;
    }
  }

  private int readCharTimeout(NonBlockingReader nbReader, long timeoutMs) {
    try {
      return nbReader.read(timeoutMs);
    } catch (Exception e) {
      return NonBlockingReader.EOF;
    }
  }

  private int overlayInnerWidth(int cols) {
    return Math.max(cols - HORIZONTAL_MARGIN * 2 - 2, 10);
  }

  private static String stripAnsi(String s) {
    return s.replaceAll("\033\\[[0-9;]*[A-Za-z]", "");
  }

  private String padRight(String s, int width) {
    if (s.length() >= width) {
      return s.substring(0, width);
    }
    return s + " ".repeat(width - s.length());
  }

  private boolean isDumb() {
    return "dumb".equals(terminal.getType());
  }
}
