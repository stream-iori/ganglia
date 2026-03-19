package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import work.ganglia.port.external.tool.ObservationEvent;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Renders ObservationEvents to the terminal with Unicode box-drawing tool cards,
 * dot-prefixed response output, and StatusBar integration.
 */
public class EventRenderer {

    private static final String BOX_TOP_LEFT = "\u256d";
    private static final String BOX_TOP_RIGHT = "\u256e";
    private static final String BOX_BOTTOM_LEFT = "\u2570";
    private static final String BOX_BOTTOM_RIGHT = "\u256f";
    private static final String BOX_HORIZONTAL = "\u2500";
    private static final String BOX_VERTICAL = "\u2502";

    /** Green filled circle for assistant response lines. */
    private static final String RESPONSE_DOT = "\u25cf";
    /** Dim dot for continuation lines. */
    private static final String CONTINUATION_DOT = "\u2502";

    private static final int MAX_PREVIEW_LINES = 8;
    private static final int CARD_MARGIN = 4;

    private final Terminal terminal;
    private final PrintWriter writer;
    private final MarkdownRenderer markdownRenderer;
    private final StatusBar statusBar;

    // Tool card state
    private String currentToolName;
    private long toolStartTime;
    private boolean insideToolCard;

    // Token accumulation and response state
    private final StringBuilder accumulatedTokens = new StringBuilder();
    private String lastRenderedResponse = null;
    private boolean responseRendered = false;
    private boolean lastResponseExpanded = false;

    public EventRenderer(Terminal terminal, MarkdownRenderer markdownRenderer, StatusBar statusBar) {
        this.terminal = terminal;
        this.writer = terminal.writer();
        this.markdownRenderer = markdownRenderer;
        this.statusBar = statusBar;
    }

    /**
     * Renders a single observation event to the terminal.
     */
    public void render(ObservationEvent event) {
        switch (event.type()) {
            case TURN_STARTED -> handleTurnStarted();
            case TOKEN_RECEIVED -> handleTokenReceived(event);
            case REASONING_STARTED -> handleReasoningStarted();
            case REASONING_FINISHED -> handleReasoningFinished();
            case TOOL_STARTED -> handleToolStarted(event);
            case TOOL_OUTPUT_STREAM -> handleToolOutputStream(event);
            case TOOL_FINISHED -> handleToolFinished(event);
            case ERROR -> handleError(event);
            case TURN_FINISHED -> handleTurnFinished();
            default -> {}
        }
    }

    private void handleTurnStarted() {
        accumulatedTokens.setLength(0);
        responseRendered = false;
        statusBar.setThinking();
    }

    private void handleTokenReceived(ObservationEvent event) {
        if (event.content() != null) {
            accumulatedTokens.append(event.content());
            writer.print("\r\033[2K");
            writer.print("Generating... (" + accumulatedTokens.length() + " chars)");
            writer.flush();
        }
    }

    private void handleReasoningStarted() {
        statusBar.setThinking();
        writer.println(new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).italic())
                .append("Thinking...")
                .toAnsi());
        writer.flush();
    }

    private void handleReasoningFinished() {
        writer.print("\r\033[2K");
        if (accumulatedTokens.length() > 0) {
            lastRenderedResponse = accumulatedTokens.toString();
            renderResponse(lastRenderedResponse, false);
            responseRendered = true;
        }
        writer.flush();
    }

    private void handleToolStarted(ObservationEvent event) {
        String toolName = event.content() != null ? event.content() : "unknown";
        this.currentToolName = toolName;
        this.toolStartTime = System.currentTimeMillis();
        this.insideToolCard = true;
        statusBar.setExecutingTool(toolName);

        int width = getCardWidth();
        writer.println();

        String label = " " + toolName + " ";
        int fillLen = width - 2 - label.length() - 1;
        if (fillLen < 0) fillLen = 0;
        writer.println(new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(BOX_TOP_LEFT)
                .append(BOX_HORIZONTAL)
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold())
                .append(label)
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(BOX_HORIZONTAL.repeat(fillLen))
                .append(BOX_TOP_RIGHT)
                .toAnsi());

        if (event.data() != null && !event.data().isEmpty()) {
            String input = extractToolInput(event);
            if (input != null) {
                printCardLine(input, width);
            }
        }
        writer.flush();
    }

    private void handleToolOutputStream(ObservationEvent event) {
        if (!insideToolCard) return;
        if (event.content() != null) {
            int width = getCardWidth();
            for (String line : event.content().split("\n", -1)) {
                printCardLine(line, width);
            }
            writer.flush();
        }
    }

    private void handleToolFinished(ObservationEvent event) {
        if (!insideToolCard) {
            printSimpleToolFinished(event);
            statusBar.setIdle();
            return;
        }

        int width = getCardWidth();
        long elapsed = System.currentTimeMillis() - toolStartTime;
        String timeStr = formatDuration(elapsed);
        boolean isError = event.content() != null && event.content().startsWith("Error:");

        if (isError && event.content() != null) {
            printCardLine(event.content(), width);
        }

        String status = isError ? "\u2717 " + timeStr : "\u2713 " + timeStr;
        String statusSuffix = " " + status + " ";
        int fillBefore = width - 2 - statusSuffix.length() - 4;
        if (fillBefore < 0) fillBefore = 0;

        AttributedStyle statusColor = isError
                ? AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)
                : AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);

        writer.println(new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(BOX_BOTTOM_LEFT)
                .append(BOX_HORIZONTAL.repeat(fillBefore))
                .style(statusColor)
                .append(statusSuffix)
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append(BOX_HORIZONTAL.repeat(4))
                .append(BOX_BOTTOM_RIGHT)
                .toAnsi());
        writer.flush();

        this.insideToolCard = false;
        this.currentToolName = null;
        statusBar.setIdle();
    }

    private void handleError(ObservationEvent event) {
        writer.println();
        writer.print(markdownRenderer.render("### ERROR\n" + event.content()));
        writer.flush();
        statusBar.setIdle();
    }

    private void handleTurnFinished() {
        writer.print("\r\033[2K");
        String content = accumulatedTokens.toString();
        if (!content.isEmpty() && !responseRendered) {
            lastRenderedResponse = content;
            renderResponse(content, false);
        }
        writer.println();
        writer.flush();
        statusBar.setIdle();
    }

    // ── Response rendering (dot-prefixed, like Claude Code) ──────────────

    /**
     * Renders response content with a green ● dot prefix.
     * When collapsed (full=false), shows up to MAX_PREVIEW_LINES with a truncation hint.
     * When expanded (full=true), shows all lines.
     */
    private void renderResponse(String content, boolean full) {
        int contentWidth = getResponseContentWidth();
        MarkdownRenderer responseMd = new MarkdownRenderer();
        String rendered = responseMd.render(content);
        List<String> lines = splitAndWrap(rendered, contentWidth);

        // Trim trailing empties
        int lastNonEmpty = lines.size() - 1;
        while (lastNonEmpty >= 0 && lines.get(lastNonEmpty).trim().isEmpty()) {
            lastNonEmpty--;
        }
        if (lastNonEmpty < 0) return;
        List<String> trimmedLines = lines.subList(0, lastNonEmpty + 1);

        boolean truncated = !full && trimmedLines.size() > MAX_PREVIEW_LINES;
        int displayCount = truncated ? MAX_PREVIEW_LINES : trimmedLines.size();

        AttributedStyle dotStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
        AttributedStyle contStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).faint();

        writer.println();
        for (int i = 0; i < displayCount; i++) {
            String dot = (i == 0) ? RESPONSE_DOT : CONTINUATION_DOT;
            AttributedStyle style = (i == 0) ? dotStyle : contStyle;
            writer.println(new AttributedStringBuilder()
                    .style(style)
                    .append(dot)
                    .style(AttributedStyle.DEFAULT)
                    .append(" " + trimmedLines.get(i))
                    .toAnsi());
        }

        if (truncated) {
            int remaining = trimmedLines.size() - MAX_PREVIEW_LINES;
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.faint())
                    .append("  ... +" + remaining + " lines (Ctrl+O to expand)")
                    .toAnsi());
        }

        lastResponseExpanded = full;
    }

    /**
     * Toggles between expanded and collapsed view of the last response.
     */
    public void toggleLastResponse() {
        if (lastRenderedResponse == null) {
            writer.println("(No response to expand)");
            writer.flush();
            return;
        }
        renderResponse(lastRenderedResponse, !lastResponseExpanded);
        writer.flush();
    }

    /**
     * Returns the last rendered response content (raw markdown), or null if none.
     */
    public String getLastRenderedResponse() {
        return lastRenderedResponse;
    }

    public boolean isLastResponseExpanded() {
        return lastResponseExpanded;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private int getResponseContentWidth() {
        int termWidth = terminal.getWidth();
        if (termWidth <= 0) termWidth = 80;
        // "● " prefix takes 2 chars + 2 margin
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
                while (end < line.length() && line.charAt(end) != 'm') end++;
                if (end < line.length()) end++;
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

    private void printCardLine(String text, int width) {
        int contentWidth = width - 4;
        List<String> wrapped = new ArrayList<>();
        wrapLine(text, contentWidth, wrapped);
        for (String segment : wrapped) {
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                    .append(BOX_VERTICAL)
                    .style(AttributedStyle.DEFAULT)
                    .append(" " + segment)
                    .toAnsi());
        }
    }

    private void printSimpleToolFinished(ObservationEvent event) {
        if (event.content() != null && event.content().startsWith("Error:")) {
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                    .append("[\u2717 Failed] ")
                    .append(event.content())
                    .toAnsi());
        } else {
            writer.println(new AttributedStringBuilder()
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                    .append("[\u2713 Done]")
                    .toAnsi());
        }
        writer.flush();
    }

    private String extractToolInput(ObservationEvent event) {
        var data = event.data();
        if ("run_shell_command".equals(currentToolName) && data.containsKey("command")) {
            return "$ " + data.get("command");
        }
        return data.toString();
    }

    private int getCardWidth() {
        int termWidth = terminal.getWidth();
        if (termWidth <= 0) termWidth = 80;
        return Math.max(termWidth - CARD_MARGIN, 40);
    }

    private String formatDuration(long millis) {
        if (millis < 1000) return millis + "ms";
        double secs = millis / 1000.0;
        return String.format("%.1fs", secs);
    }

    public StringBuilder getAccumulatedTokens() {
        return accumulatedTokens;
    }
}
