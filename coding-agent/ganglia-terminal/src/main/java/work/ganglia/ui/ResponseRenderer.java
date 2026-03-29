package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Renders assistant responses with dot-prefixed lines and supports collapse/expand toggling via
 * append mode.
 */
public class ResponseRenderer {

  private static final String RESPONSE_DOT = "\u25cf";
  private static final String CONTINUATION_DOT = "\u2502";
  private static final int MAX_PREVIEW_LINES = 8;

  private final Terminal terminal;
  private final PrintWriter writer;
  private final MarkdownRenderer markdownRenderer;
  private final StatusBar statusBar;

  private String lastRenderedResponse = null;
  private boolean lastResponseExpanded = false;
  private boolean responseRendered = false;
  private volatile boolean toggleRequested = false;

  public ResponseRenderer(
      Terminal terminal, MarkdownRenderer markdownRenderer, StatusBar statusBar) {
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.markdownRenderer = markdownRenderer;
    this.statusBar = statusBar;
  }

  /** Resets the response-rendered flag for a new turn. */
  public void resetForNewTurn() {
    responseRendered = false;
  }

  public boolean isResponseRendered() {
    return responseRendered;
  }

  /** Renders response content with a green dot prefix to the terminal. */
  public void render(String content, boolean full) {
    synchronized (statusBar.terminalWriteLock) {
      List<String> lines = buildResponseLines(content, full);
      if (lines.isEmpty()) {
        return;
      }
      responseRendered = true;
      for (String line : lines) {
        writer.println(line);
      }
      lastRenderedResponse = content;
      lastResponseExpanded = full;
    }
  }

  /** Toggles between expanded and collapsed view by appending the re-rendered response. */
  public void toggleAppend() {
    if (lastRenderedResponse == null) {
      writer.println("(No response to expand)");
      writer.flush();
      return;
    }
    render(lastRenderedResponse, !lastResponseExpanded);
    writer.flush();
  }

  public boolean canToggle() {
    return lastRenderedResponse != null;
  }

  public void requestToggle() {
    toggleRequested = true;
  }

  public boolean consumeToggleRequest() {
    if (toggleRequested) {
      toggleRequested = false;
      return true;
    }
    return false;
  }

  public String getLastRenderedResponse() {
    return lastRenderedResponse;
  }

  public boolean isLastResponseExpanded() {
    return lastResponseExpanded;
  }

  // ── Internal ─────────────────────────────────────────────────────────

  private List<String> buildResponseLines(String content, boolean full) {
    int contentWidth = getResponseContentWidth();
    MarkdownRenderer responseMd = new MarkdownRenderer();
    String rendered = responseMd.render(content);
    List<String> lines = splitAndWrap(rendered, contentWidth);

    int lastNonEmpty = lines.size() - 1;
    while (lastNonEmpty >= 0 && lines.get(lastNonEmpty).trim().isEmpty()) {
      lastNonEmpty--;
    }
    if (lastNonEmpty < 0) {
      return List.of();
    }
    List<String> trimmedLines = lines.subList(0, lastNonEmpty + 1);

    boolean truncated = !full && trimmedLines.size() > MAX_PREVIEW_LINES;
    int displayCount = truncated ? MAX_PREVIEW_LINES : trimmedLines.size();

    AttributedStyle dotStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
    AttributedStyle contStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).faint();

    List<String> result = new ArrayList<>();
    result.add("");
    for (int i = 0; i < displayCount; i++) {
      String dot = (i == 0) ? RESPONSE_DOT : CONTINUATION_DOT;
      AttributedStyle style = (i == 0) ? dotStyle : contStyle;
      AttributedStringBuilder asb =
          new AttributedStringBuilder()
              .style(style)
              .append(dot)
              .style(AttributedStyle.DEFAULT)
              .append(" " + trimmedLines.get(i));
      result.add(toAnsiFallback(asb));
    }

    if (truncated) {
      int remaining = trimmedLines.size() - MAX_PREVIEW_LINES;
      AttributedStringBuilder asb =
          new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.faint())
              .append("  ... +" + remaining + " lines (Ctrl+O toggle)");
      result.add(toAnsiFallback(asb));
    }

    return result;
  }

  private String toAnsiFallback(AttributedStringBuilder asb) {
    if (terminal != null) {
      return asb.toAnsi(terminal);
    }
    return asb.toAnsi();
  }

  private int getResponseContentWidth() {
    int termWidth = terminal.getWidth();
    if (termWidth <= 0) {
      termWidth = 80;
    }
    return Math.max(termWidth - 4, 40);
  }

  private List<String> splitAndWrap(String rendered, int maxWidth) {
    List<String> result = new ArrayList<>();
    for (String line : rendered.split("\n", -1)) {
      int displayLen = stripAnsi(line).length();
      if (displayLen > maxWidth) {
        wrapLine(line, maxWidth, result);
      } else {
        result.add(line);
      }
    }
    return result;
  }

  private static String stripAnsi(String s) {
    return s.replaceAll("\033\\[[0-9;]*m", "");
  }

  private static void wrapLine(String line, int maxWidth, List<String> out) {
    String stripped = stripAnsi(line);
    if (stripped.length() <= maxWidth) {
      out.add(line);
      return;
    }
    StringBuilder current = new StringBuilder();
    int displayPos = 0;
    int i = 0;
    while (i < line.length()) {
      if (line.charAt(i) == '\033' && i + 1 < line.length() && line.charAt(i + 1) == '[') {
        int end = i + 2;
        while (end < line.length() && line.charAt(end) != 'm') {
          end++;
        }
        if (end < line.length()) {
          end++;
        }
        current.append(line, i, end);
        i = end;
      } else {
        if (displayPos >= maxWidth) {
          out.add(current.toString());
          current.setLength(0);
          displayPos = 0;
        }
        current.append(line.charAt(i));
        displayPos++;
        i++;
      }
    }
    if (current.length() > 0) {
      out.add(current.toString());
    }
  }
}
