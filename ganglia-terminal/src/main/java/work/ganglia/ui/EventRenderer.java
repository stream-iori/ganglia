package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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

  /** Holds the pending ask data when a USER_INTERACTION_REQUIRED event is received. */
  private final AtomicReference<PendingAsk> pendingAsk = new AtomicReference<>();

  /** Callback invoked on the Vert.x event loop when an ask event arrives. */
  private Consumer<PendingAsk> askHandler;

  /** Encapsulates the data from a USER_INTERACTION_REQUIRED event. */
  public record PendingAsk(String askId, List<Map<String, Object>> questions, String toolCallId) {}

  public void setAskHandler(Consumer<PendingAsk> handler) {
    this.askHandler = handler;
  }

  public PendingAsk consumePendingAsk() {
    return pendingAsk.getAndSet(null);
  }

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
      case USER_INTERACTION_REQUIRED -> handleUserInteractionRequired(event);
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

  @SuppressWarnings("unchecked")
  private void handleUserInteractionRequired(ObservationEvent event) {
    Map<String, Object> data = event.data();
    if (data == null) {
      return;
    }
    String askId = (String) data.get("askId");
    String toolCallId = (String) data.get("toolCallId");
    List<Map<String, Object>> questions = (List<Map<String, Object>>) data.get("questions");
    if (askId == null || questions == null || questions.isEmpty()) {
      return;
    }
    PendingAsk ask = new PendingAsk(askId, questions, toolCallId);
    pendingAsk.set(ask);
    if (askHandler != null) {
      askHandler.accept(ask);
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
      // Reset per-iteration state: each reasoning phase accumulates its own tokens.
      accumulatedTokens.setLength(0);
      response.resetForNewTurn();
      statusBar.setThinking();
      writer.print(AnsiCodes.moveTo(statusBar.getScrollBottom(), 1));
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
      writer.print(AnsiCodes.moveTo(statusBar.getScrollBottom(), 1));
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

  /** Toggles the last response between expanded and collapsed by appending below. */
  public void toggleLastResponse() {
    response.toggleAppend();
  }

  /**
   * Alias kept for TerminalApp Ctrl+O handler — routes to append-based toggle.
   *
   * @deprecated Use {@link #toggleLastResponse()} directly.
   */
  public void toggleLastResponseInPlace() {
    response.toggleAppend();
  }

  public void requestToggle() {
    // Flag consumed by consumeToggleRequest(); nothing else needed since
    // toggleAppend() handles everything at flush time.
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
