package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import work.ganglia.port.external.tool.ObservationEvent;

/**
 * Renders tool execution cards with spinner animation to the terminal. Handles TOOL_STARTED,
 * TOOL_OUTPUT_STREAM, and TOOL_FINISHED events.
 */
public class ToolCardRenderer {

  private static final String[] SPINNER = {
    "\u280b", "\u2819", "\u2839", "\u2838", "\u283c", "\u2834", "\u2826", "\u2827", "\u2807",
    "\u280f"
  };
  private static final int SPINNER_INTERVAL_MS = 100;

  private final PrintWriter writer;
  private final StatusBar statusBar;

  private String currentToolName;
  private long toolStartTime;
  private boolean insideToolCard;
  private Timer spinnerTimer;
  private int spinnerFrame;
  private String toolLinePrefix = "";

  /** Absolute row of the last line written in the scroll region (for spinner redraws). */
  private int lastWrittenRow;

  // Accumulation fields for DetailView
  private List<String> pendingOutputLines;
  private String pendingParams;
  private ToolCard lastCard;

  public ToolCardRenderer(PrintWriter writer, StatusBar statusBar) {
    this.writer = writer;
    this.statusBar = statusBar;
  }

  public void start(ObservationEvent event) {
    String toolName = event.content() != null ? event.content() : "unknown";
    this.currentToolName = toolName;
    this.toolStartTime = System.currentTimeMillis();
    this.insideToolCard = true;
    this.pendingOutputLines = new ArrayList<>();
    statusBar.setExecutingTool(toolName);

    String params = formatToolParams(event);
    // Truncate params to prevent uncontrolled wrapping in the scroll region
    int maxParamLen = Math.max(20, statusBar.getCols() - toolName.length() - 12);
    if (params.length() > maxParamLen) {
      params = params.substring(0, maxParamLen - 3) + "...";
    }
    this.pendingParams = params;
    toolLinePrefix =
        new AttributedStringBuilder()
            .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
            .append("  " + toolName)
            .style(AttributedStyle.DEFAULT)
            .append(params.isEmpty() ? "" : "  " + params)
            .toAnsi();

    synchronized (statusBar.terminalWriteLock) {
      writer.print(String.format("\033[%d;1H", statusBar.getScrollBottom()));
      writer.println();
      writer.print(toolLinePrefix);
      lastWrittenRow = statusBar.getScrollBottom();
      statusBar.parkCursorAtInput();
      writer.flush();
    }
    startSpinner();
  }

  public void appendOutput(ObservationEvent event) {
    if (!insideToolCard) {
      return;
    }
    if (event.content() != null) {
      String[] lines = event.content().split("\n", -1);
      if (pendingOutputLines != null) {
        Collections.addAll(pendingOutputLines, lines);
      }
      synchronized (statusBar.terminalWriteLock) {
        writer.print(String.format("\033[%d;1H", statusBar.getScrollBottom()));
        writer.println();
        for (String line : lines) {
          writer.println(
              new AttributedStringBuilder()
                  .style(AttributedStyle.DEFAULT.faint())
                  .append("    " + line)
                  .toAnsi());
        }
        lastWrittenRow = statusBar.getScrollBottom();
        statusBar.parkCursorAtInput();
        writer.flush();
      }
    }
  }

  public void finish(ObservationEvent event) {
    stopSpinner();

    if (!insideToolCard) {
      printSimpleToolFinished(event);
      statusBar.setIdle();
      return;
    }

    long elapsed = System.currentTimeMillis() - toolStartTime;
    String timeStr = formatDuration(elapsed);
    boolean isError = event.content() != null && event.content().startsWith("Error:");

    if (isError) {
      writer.print(AnsiCodes.moveAndClear(lastWrittenRow));
      writer.println(
          new AttributedStringBuilder()
              .append(toolLinePrefix + "  ")
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
              .append("\u2717 " + timeStr)
              .append("  " + (event.content() != null ? event.content() : ""))
              .toAnsi());
    } else {
      writer.print(AnsiCodes.moveAndClear(lastWrittenRow));
      writer.println(
          new AttributedStringBuilder()
              .append(toolLinePrefix + "  ")
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
              .append("\u2713 " + timeStr)
              .toAnsi());
    }
    statusBar.parkCursorAtInput();
    writer.flush();

    this.lastCard =
        new ToolCard(
            currentToolName,
            pendingParams != null ? pendingParams : "",
            pendingOutputLines != null ? List.copyOf(pendingOutputLines) : List.of(),
            event.content(),
            isError,
            elapsed);

    this.insideToolCard = false;
    this.currentToolName = null;
    this.pendingOutputLines = null;
    this.pendingParams = null;
    statusBar.setIdle();
  }

  public ToolCard getLastCard() {
    return lastCard;
  }

  // ── Spinner animation ────────────────────────────────────────────────

  private void startSpinner() {
    synchronized (statusBar.terminalWriteLock) {
      spinnerFrame = 0;
      spinnerTimer = new Timer("tool-spinner", true);
      spinnerTimer.scheduleAtFixedRate(
          new TimerTask() {
            @Override
            public void run() {
              synchronized (statusBar.terminalWriteLock) {
                if (spinnerTimer == null) {
                  return;
                }
                long elapsed = System.currentTimeMillis() - toolStartTime;
                String time = formatDuration(elapsed);
                String frame = SPINNER[spinnerFrame % SPINNER.length];
                spinnerFrame++;
                writer.print(AnsiCodes.moveAndClear(lastWrittenRow));
                writer.print(
                    new AttributedStringBuilder()
                        .append(toolLinePrefix + "  ")
                        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                        .append(frame + " ")
                        .style(AttributedStyle.DEFAULT.faint())
                        .append(time)
                        .toAnsi());
                statusBar.parkCursorAtInput();
                writer.flush();
              }
            }
          },
          0,
          SPINNER_INTERVAL_MS);
    }
  }

  private void stopSpinner() {
    synchronized (statusBar.terminalWriteLock) {
      if (spinnerTimer != null) {
        spinnerTimer.cancel();
        spinnerTimer = null;
      }
    }
  }

  // ── Helpers ──────────────────────────────────────────────────────────

  private void printSimpleToolFinished(ObservationEvent event) {
    if (event.content() != null && event.content().startsWith("Error:")) {
      writer.println(
          new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
              .append("  \u2717 ")
              .append(event.content())
              .toAnsi());
    } else {
      writer.println(
          new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
              .append("  \u2713 Done")
              .toAnsi());
    }
    statusBar.parkCursorAtInput();
    writer.flush();
  }

  private String formatToolParams(ObservationEvent event) {
    var data = event.data();
    if (data == null || data.isEmpty()) {
      return "";
    }

    if ("run_shell_command".equals(currentToolName) && data.containsKey("command")) {
      return "$ " + data.get("command");
    }

    StringBuilder sb = new StringBuilder();
    for (var entry : data.entrySet()) {
      if ("toolCallId".equals(entry.getKey())) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append("  ");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue());
    }
    return sb.toString();
  }

  private String formatDuration(long millis) {
    return AnsiCodes.formatDuration(millis);
  }
}
