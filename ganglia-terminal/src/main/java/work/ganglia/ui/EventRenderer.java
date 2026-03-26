package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.Map;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import work.ganglia.port.external.tool.ObservationEvent;

/**
 * Thin dispatch layer that routes ObservationEvents to specialized renderers. Manages token
 * accumulation and turn/reasoning lifecycle; delegates tool card rendering to {@link
 * ToolCardRenderer} and response rendering to {@link ResponseRenderer}.
 */
public class EventRenderer {

  private final Terminal terminal;
  private final PrintWriter writer;
  private final MarkdownRenderer markdownRenderer;
  private final StatusBar statusBar;
  private final ToolCardRenderer toolCard;
  private final ResponseRenderer response;
  private TaskPanelRenderer taskPanel;

  private final StringBuilder accumulatedTokens = new StringBuilder();

  public EventRenderer(Terminal terminal, MarkdownRenderer markdownRenderer, StatusBar statusBar) {
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.markdownRenderer = markdownRenderer;
    this.statusBar = statusBar;
    this.toolCard = new ToolCardRenderer(writer, statusBar);
    this.response = new ResponseRenderer(terminal, markdownRenderer, statusBar);
  }

  public void setTaskPanel(TaskPanelRenderer taskPanel) {
    this.taskPanel = taskPanel;
  }

  /** Renders a single observation event to the terminal. */
  public void render(ObservationEvent event) {
    switch (event.type()) {
      case TURN_STARTED -> handleTurnStarted();
      case TOKEN_RECEIVED -> handleTokenReceived(event);
      case REASONING_STARTED -> handleReasoningStarted();
      case REASONING_FINISHED -> handleReasoningFinished();
      case TOOL_STARTED -> toolCard.start(event);
      case TOOL_OUTPUT_STREAM -> toolCard.appendOutput(event);
      case TOOL_FINISHED -> toolCard.finish(event);
      case PLAN_UPDATED -> handlePlanUpdated(event);
      case ERROR -> handleError(event);
      case TURN_FINISHED -> handleTurnFinished();
      default -> {}
    }
  }

  private void handleTurnStarted() {
    accumulatedTokens.setLength(0);
    response.resetForNewTurn();
    statusBar.setThinking();
    if (taskPanel != null) {
      taskPanel.onTurnStarted();
    }
  }

  @SuppressWarnings("unchecked")
  private void handlePlanUpdated(ObservationEvent event) {
    if (taskPanel == null) {
      return;
    }
    Map<String, Object> data = event.data();
    if (data != null && data.containsKey("plan")) {
      taskPanel.updatePlanFromData(data.get("plan"));
      statusBar.recalculateLayout();
    }
  }

  private void handleTokenReceived(ObservationEvent event) {
    synchronized (statusBar.terminalWriteLock) {
      if (event.content() != null) {
        accumulatedTokens.append(event.content());
        writer.print(AnsiCodes.moveAndClear(statusBar.getScrollBottom()));
        writer.print("Generating... (" + accumulatedTokens.length() + " chars)");
        statusBar.parkCursorAtInput();
        writer.flush();
      }
    }
  }

  private void handleReasoningStarted() {
    synchronized (statusBar.terminalWriteLock) {
      statusBar.setThinking();
      // Move to scroll region before writing
      writer.print(String.format("\033[%d;1H", statusBar.getScrollBottom()));
      writer.println();
      writer.println(
          new AttributedStringBuilder()
              .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).italic())
              .append("  Thinking...")
              .toAnsi());
      writer.println();
      statusBar.parkCursorAtInput();
      writer.flush();
    }
  }

  private void handleReasoningFinished() {
    synchronized (statusBar.terminalWriteLock) {
      writer.print(AnsiCodes.moveAndClear(statusBar.getScrollBottom()));
      String content = accumulatedTokens.toString();
      if (!content.isEmpty() && !response.isResponseRendered()) {
        response.render(content, false);
      }
      statusBar.parkCursorAtInput();
      writer.flush();
    }
  }

  private void handleError(ObservationEvent event) {
    synchronized (statusBar.terminalWriteLock) {
      writer.print(String.format("\033[%d;1H", statusBar.getScrollBottom()));
      writer.println();
      writer.print(markdownRenderer.render("### ERROR\n" + event.content()));
      statusBar.setIdle();
      statusBar.parkCursorAtInput();
      writer.flush();
    }
  }

  private void handleTurnFinished() {
    synchronized (statusBar.terminalWriteLock) {
      writer.print(AnsiCodes.moveAndClear(statusBar.getScrollBottom()));
      String content = accumulatedTokens.toString();
      if (!content.isEmpty() && !response.isResponseRendered()) {
        response.render(content, false);
      }
      writer.println();
      statusBar.setIdle();
      statusBar.parkCursorAtInput();
      writer.flush();
    }
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
