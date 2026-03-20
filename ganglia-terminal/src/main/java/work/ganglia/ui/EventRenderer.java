package work.ganglia.ui;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import work.ganglia.port.external.tool.ObservationEvent;

import java.io.PrintWriter;

/**
 * Thin dispatch layer that routes ObservationEvents to specialized renderers.
 * Manages token accumulation and turn/reasoning lifecycle; delegates tool card
 * rendering to {@link ToolCardRenderer} and response rendering to {@link ResponseRenderer}.
 */
public class EventRenderer {

    private final Terminal terminal;
    private final PrintWriter writer;
    private final MarkdownRenderer markdownRenderer;
    private final StatusBar statusBar;
    private final ToolCardRenderer toolCard;
    private final ResponseRenderer response;

    private final StringBuilder accumulatedTokens = new StringBuilder();

    public EventRenderer(Terminal terminal, MarkdownRenderer markdownRenderer, StatusBar statusBar) {
        this.terminal = terminal;
        this.writer = terminal.writer();
        this.markdownRenderer = markdownRenderer;
        this.statusBar = statusBar;
        this.toolCard = new ToolCardRenderer(writer, statusBar);
        this.response = new ResponseRenderer(terminal, markdownRenderer);
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
            case TOOL_STARTED -> toolCard.start(event);
            case TOOL_OUTPUT_STREAM -> toolCard.appendOutput(event);
            case TOOL_FINISHED -> toolCard.finish(event);
            case ERROR -> handleError(event);
            case TURN_FINISHED -> handleTurnFinished();
            default -> {}
        }
    }

    private void handleTurnStarted() {
        accumulatedTokens.setLength(0);
        response.resetForNewTurn();
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
            response.render(accumulatedTokens.toString(), false);
        }
        writer.flush();
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
        if (!content.isEmpty() && !response.isResponseRendered()) {
            response.render(content, false);
        }
        writer.println();
        writer.flush();
        statusBar.setIdle();
    }

    // ── Delegate methods (preserve public API for TerminalApp & tests) ───

    public void toggleLastResponse() {
        response.toggleAppend();
    }

    public void toggleLastResponseInPlace() {
        response.toggleInPlace();
    }

    public void requestToggle() {
        response.requestToggle();
    }

    public boolean canToggle() {
        return response.canToggle();
    }

    public boolean consumeToggleRequest() {
        return response.consumeToggleRequest();
    }

    public String getLastRenderedResponse() {
        return response.getLastRenderedResponse();
    }

    public boolean isLastResponseExpanded() {
        return response.isLastResponseExpanded();
    }

    public ToolCard getLastToolCard() {
        return toolCard.getLastCard();
    }

    public StringBuilder getAccumulatedTokens() {
        return accumulatedTokens;
    }
}
