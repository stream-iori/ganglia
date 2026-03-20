package work.ganglia.ui;

import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import work.ganglia.port.external.tool.ObservationEvent;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Renders tool execution cards with spinner animation to the terminal.
 * Handles TOOL_STARTED, TOOL_OUTPUT_STREAM, and TOOL_FINISHED events.
 */
public class ToolCardRenderer {

    private static final String[] SPINNER = {
        "\u280b", "\u2819", "\u2839", "\u2838", "\u283c", "\u2834", "\u2826", "\u2827", "\u2807", "\u280f"
    };
    private static final int SPINNER_INTERVAL_MS = 100;

    private final PrintWriter writer;
    private final StatusBar statusBar;

    private String currentToolName;
    private long toolStartTime;
    private boolean insideToolCard;
    private final Object spinnerLock = new Object();
    private Timer spinnerTimer;
    private int spinnerFrame;
    private String toolLinePrefix = "";

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
        this.pendingParams = params;
        toolLinePrefix = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                .append("  " + toolName)
                .style(AttributedStyle.DEFAULT)
                .append(params.isEmpty() ? "" : "  " + params)
                .toAnsi();

        synchronized (spinnerLock) {
            writer.println();
            writer.print(toolLinePrefix);
            writer.flush();
        }
        startSpinner();
    }

    public void appendOutput(ObservationEvent event) {
        if (!insideToolCard) return;
        if (event.content() != null) {
            String[] lines = event.content().split("\n", -1);
            if (pendingOutputLines != null) {
                Collections.addAll(pendingOutputLines, lines);
            }
            synchronized (spinnerLock) {
                writer.println();
                for (String line : lines) {
                    writer.println(new AttributedStringBuilder()
                            .style(AttributedStyle.DEFAULT.faint())
                            .append("    " + line)
                            .toAnsi());
                }
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
            writer.print("\r\033[2K");
            writer.println(new AttributedStringBuilder()
                    .append(toolLinePrefix + "  ")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                    .append("\u2717 " + timeStr)
                    .append("  " + (event.content() != null ? event.content() : ""))
                    .toAnsi());
        } else {
            writer.print("\r\033[2K");
            writer.println(new AttributedStringBuilder()
                    .append(toolLinePrefix + "  ")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                    .append("\u2713 " + timeStr)
                    .toAnsi());
        }
        writer.flush();

        this.lastCard = new ToolCard(
                currentToolName,
                pendingParams != null ? pendingParams : "",
                pendingOutputLines != null ? List.copyOf(pendingOutputLines) : List.of(),
                event.content(),
                isError,
                elapsed
        );

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
        synchronized (spinnerLock) {
            spinnerFrame = 0;
            spinnerTimer = new Timer("tool-spinner", true);
            spinnerTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    synchronized (spinnerLock) {
                        if (spinnerTimer == null) return;
                        long elapsed = System.currentTimeMillis() - toolStartTime;
                        String time = formatDuration(elapsed);
                        String frame = SPINNER[spinnerFrame % SPINNER.length];
                        spinnerFrame++;
                        writer.print("\r\033[2K");
                        writer.print(new AttributedStringBuilder()
                                .append(toolLinePrefix + "  ")
                                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                                .append(frame + " ")
                                .style(AttributedStyle.DEFAULT.faint())
                                .append(time)
                                .toAnsi());
                        writer.flush();
                    }
                }
            }, 0, SPINNER_INTERVAL_MS);
        }
    }

    private void stopSpinner() {
        synchronized (spinnerLock) {
            if (spinnerTimer != null) {
                spinnerTimer.cancel();
                spinnerTimer = null;
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void printSimpleToolFinished(ObservationEvent event) {
        if (event.content() != null && event.content().startsWith("Error:")) {
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                    .append("  \u2717 ")
                    .append(event.content())
                    .toAnsi());
        } else {
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                    .append("  \u2713 Done")
                    .toAnsi());
        }
        writer.flush();
    }

    private String formatToolParams(ObservationEvent event) {
        var data = event.data();
        if (data == null || data.isEmpty()) return "";

        if ("run_shell_command".equals(currentToolName) && data.containsKey("command")) {
            return "$ " + data.get("command");
        }

        StringBuilder sb = new StringBuilder();
        for (var entry : data.entrySet()) {
            if ("toolCallId".equals(entry.getKey())) continue;
            if (sb.length() > 0) sb.append("  ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        double secs = millis / 1000.0;
        return String.format("%.1fs", secs);
    }
}
