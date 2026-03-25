package work.ganglia.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.jline.keymap.KeyMap;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.Ganglia;
import work.ganglia.kernel.loop.AgentAbortedException;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.internal.state.AgentSignal;

/**
 * Main interactive REPL application for the Ganglia terminal CLI. Orchestrates JLine LineReader,
 * EventRenderer, StatusBar, and the Ganglia agent loop.
 *
 * <p>Threading model:
 *
 * <ul>
 *   <li>Thread 1 (Input): Blocks on LineReader.readLine(), dispatches to Vert.x, waits for turn
 *       completion
 *   <li>Thread 2 (Vert.x Event Loop): EventBus → EventRenderer → terminal.writer()
 * </ul>
 */
public class TerminalApp implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(TerminalApp.class);

  /**
   * Reconfigures Log4j2 to use the terminal-specific config that only logs to files. Must be called
   * BEFORE any Ganglia bootstrap or logger initialization to prevent log output from corrupting the
   * interactive terminal display.
   */
  public static void suppressConsoleLogging() {
    URL terminalConfig = TerminalApp.class.getClassLoader().getResource("log4j2-terminal.xml");
    if (terminalConfig != null) {
      try {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.setConfigLocation(terminalConfig.toURI());
        ctx.reconfigure();
      } catch (Exception e) {
        // Best effort — if reconfiguration fails, logs will still go to console
        System.err.println(
            "Warning: failed to reconfigure logging for terminal mode: " + e.getMessage());
      }
    }
  }

  private final Vertx vertx;
  private final Ganglia ganglia;
  private final Terminal terminal;
  private final LineReader reader;
  private final PrintWriter writer;
  private final StatusBar statusBar;
  private final EventRenderer eventRenderer;
  private final TaskPanelRenderer taskPanel;
  private final String sessionId;
  private final DetailView detailView;
  private final AtomicReference<AgentSignal> currentSignal = new AtomicReference<>();
  private volatile boolean running = true;
  private volatile boolean detailViewRequested = false;
  private volatile boolean slashMenuRequested = false;
  private final SlashCommandMenu slashCommandMenu;

  public TerminalApp(Vertx vertx, Ganglia ganglia, Terminal terminal) {
    this.vertx = vertx;
    this.ganglia = ganglia;
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.sessionId = "interactive-" + UUID.randomUUID().toString().substring(0, 8);

    this.taskPanel = new TaskPanelRenderer();
    this.statusBar = new StatusBar(terminal);
    this.statusBar.setTaskPanel(taskPanel);
    MarkdownRenderer mdRenderer = new MarkdownRenderer();
    this.eventRenderer = new EventRenderer(terminal, mdRenderer, statusBar);
    this.eventRenderer.setTaskPanel(taskPanel);
    this.detailView = new DetailView(terminal);
    this.slashCommandMenu = new SlashCommandMenu(terminal, statusBar);

    this.reader = LineReaderBuilder.builder().terminal(terminal).appName("Ganglia").build();

    // When '/' is typed at the start of input, request the custom slash menu
    // by setting a flag and exiting readLine via UserInterruptException.
    reader
        .getWidgets()
        .put(
            "slash-auto-complete",
            () -> {
              reader.getBuffer().write('/');
              if (reader.getBuffer().toString().equals("/")) {
                slashMenuRequested = true;
                throw new UserInterruptException("");
              }
              return true;
            });
    reader.getKeyMaps().get("main").bind(new Reference("slash-auto-complete"), "/");

    // Bind Ctrl+O to expand collapsed response.
    // Sets a flag and exits readLine via SEND_BREAK so the expand
    // happens outside of JLine (no display state corruption).
    reader
        .getWidgets()
        .put(
            "expand-response",
            () -> {
              if (eventRenderer.canToggle()) {
                eventRenderer.requestToggle();
                throw new UserInterruptException("");
              }
              return true;
            });
    reader.getKeyMaps().get("main").bind(new Reference("expand-response"), KeyMap.ctrl('O'));

    // Bind Ctrl+E to open detail view for last tool card
    reader
        .getWidgets()
        .put(
            "detail-view",
            () -> {
              if (eventRenderer.getLastToolCard() != null) {
                detailViewRequested = true;
                throw new UserInterruptException("");
              }
              return true;
            });
    reader.getKeyMaps().get("main").bind(new Reference("detail-view"), KeyMap.ctrl('E'));

    // Subscribe to observation events
    String address = "ganglia.observations." + sessionId;
    vertx
        .eventBus()
        .<JsonObject>consumer(
            address,
            message -> {
              ObservationEvent event = message.body().mapTo(ObservationEvent.class);
              eventRenderer.render(event);
            });
  }

  /** Creates a TerminalApp with a system terminal. */
  public static TerminalApp create(Vertx vertx, Ganglia ganglia) {
    Terminal terminal;
    try {
      terminal = TerminalBuilder.builder().system(true).build();
    } catch (IOException e) {
      logger.warn("Failed to initialize JLine terminal, falling back to dumb", e);
      try {
        terminal = TerminalBuilder.builder().dumb(true).build();
      } catch (IOException ex) {
        throw new RuntimeException("Critical failure initializing terminal", ex);
      }
    }
    return new TerminalApp(vertx, ganglia, terminal);
  }

  /** Prints the startup banner with model, session, and shortcut info. */
  public void printBanner() {
    String model = ganglia.configManager().getModel();
    String provider = ganglia.configManager().getProvider();

    var cyan = AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN);
    var cyanBold = cyan.bold();
    var dim = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
    var bold = AttributedStyle.DEFAULT.bold();

    // Build content lines to calculate box width
    String titleText = "Ganglia Coding Agent";
    String modelText = "Model:   " + model + " (" + provider + ")";
    String sessionText = "Session: " + sessionId;
    String ctrlCText = "Ctrl+C    Interrupt current turn";
    String ctrlOText = "Ctrl+O    Expand collapsed response";
    String ctrlEText = "Ctrl+E    View last tool output";
    String ctrlDText = "Ctrl+D    Exit";
    String helpText = "/help     List commands";

    int innerWidth =
        Math.max(
            Math.max(modelText.length(), sessionText.length()),
            Math.max(
                Math.max(ctrlCText.length(), ctrlOText.length()),
                Math.max(Math.max(ctrlEText.length(), helpText.length()), titleText.length())));
    innerWidth = Math.max(innerWidth, 40); // minimum width

    writer.println();
    // Top border
    writer.println(boxLine(cyan, "\u256d", "\u2500", "\u256e", innerWidth));
    // Title
    writer.println(boxContent(cyan, cyanBold, titleText, innerWidth));
    // Separator
    writer.println(boxLine(cyan, "\u251c", "\u2500", "\u2524", innerWidth));
    // Model
    writer.println(
        boxContentStyled(cyan, dim, bold, "Model:   ", model + " (" + provider + ")", innerWidth));
    // Session
    writer.println(boxContentStyled(cyan, dim, bold, "Session: ", sessionId, innerWidth));
    // Separator
    writer.println(boxLine(cyan, "\u251c", "\u2500", "\u2524", innerWidth));
    // Shortcuts
    writer.println(boxContent(cyan, dim, ctrlCText, innerWidth));
    writer.println(boxContent(cyan, dim, ctrlOText, innerWidth));
    writer.println(boxContent(cyan, dim, ctrlEText, innerWidth));
    writer.println(boxContent(cyan, dim, ctrlDText, innerWidth));
    writer.println(boxContent(cyan, dim, helpText, innerWidth));
    // Bottom border
    writer.println(boxLine(cyan, "\u2570", "\u2500", "\u256f", innerWidth));
    writer.println();
    writer.flush();
  }

  private String boxLine(AttributedStyle style, String left, String h, String right, int width) {
    return new AttributedStringBuilder()
        .style(style)
        .append(left)
        .append(h.repeat(width + 2))
        .append(right)
        .toAnsi();
  }

  private String boxContent(
      AttributedStyle border, AttributedStyle content, String text, int width) {
    int pad = width - text.length();
    return new AttributedStringBuilder()
        .style(border)
        .append("\u2502 ")
        .style(content)
        .append(text)
        .append(" ".repeat(Math.max(pad, 0)))
        .style(border)
        .append(" \u2502")
        .toAnsi();
  }

  private String boxContentStyled(
      AttributedStyle border,
      AttributedStyle labelStyle,
      AttributedStyle valueStyle,
      String label,
      String value,
      int width) {
    int pad = width - label.length() - value.length();
    return new AttributedStringBuilder()
        .style(border)
        .append("\u2502 ")
        .style(labelStyle)
        .append(label)
        .style(valueStyle)
        .append(value)
        .append(" ".repeat(Math.max(pad, 0)))
        .style(border)
        .append(" \u2502")
        .toAnsi();
  }

  /**
   * Starts the REPL loop. Blocks until the user exits (Ctrl+D or "exit"/"quit"). Should be called
   * from a dedicated thread, not the Vert.x event loop.
   */
  public void run() {
    // Enable status bar first so the scroll region is established.
    // All subsequent output is confined to the scroll region and
    // cannot overflow into the reserved area (input box + status).
    statusBar.enable();
    statusBar.setIdle();
    printBanner();
    // Add breathing room between the banner and the input box.
    // One INPUT_BOX_HEIGHT of space keeps them visually separate.
    for (int i = 0; i < StatusBar.INPUT_BOX_HEIGHT; i++) {
      writer.println();
    }
    writer.flush();
    taskPanel.startElapsedTimer(() -> statusBar.refresh());

    try {
      while (running) {
        // Show cursor and position at the input row for readLine
        showCursor();
        moveToInputRow();

        String line;
        try {
          line = reader.readLine(buildPrompt());
        } catch (UserInterruptException e) {
          // Custom slash command menu
          if (consumeSlashMenuRequest()) {
            String selected = slashCommandMenu.show();
            if (selected != null) {
              moveToScrollBottom();
              if (handleSlashCommand(selected)) {
                continue;
              }
            }
            continue;
          }
          // Ctrl+O toggle fires UserInterruptException to exit readLine cleanly
          if (eventRenderer.consumeToggleRequest()) {
            moveToScrollBottom();
            eventRenderer.toggleLastResponseInPlace();
            // Re-establish scroll region + status bar in case
            // expanded content scrolled beyond the visible area.
            statusBar.enable();
            statusBar.recalculateLayout();
            continue;
          }
          // Ctrl+E detail view
          if (consumeDetailViewRequest()) {
            statusBar.disable();
            detailView.show(eventRenderer.getLastToolCard());
            statusBar.enable();
            statusBar.recalculateLayout();
            continue;
          }
          handleCtrlC();
          continue;
        } catch (EndOfFileException e) {
          break;
        }

        // After readLine, move cursor back to scroll region for content output
        moveToScrollBottom();

        if (line == null || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
          break;
        }

        if (line.trim().isEmpty()) {
          continue;
        }

        // Handle slash commands
        if (line.startsWith("/")) {
          if (handleSlashCommand(line.trim())) {
            continue;
          }
        }

        // Echo the user's input into the scroll region so it stays visible
        echoUserInput(line);
        executeTurn(line);
      }
    } finally {
      showCursor();
      taskPanel.stopElapsedTimer();
      statusBar.disable();
    }
  }

  /** Moves the cursor to the input row inside the reserved bottom panel. */
  private void moveToInputRow() {
    if ("dumb".equals(terminal.getType())) {
      return;
    }
    int inputRow = statusBar.getInputRow();
    writer.print(AnsiCodes.moveAndClear(inputRow));
    writer.flush();
  }

  /**
   * Echoes the user's input into the scroll region with the same prompt style, so it remains
   * visible after the input box is repainted.
   */
  private void echoUserInput(String input) {
    if ("dumb".equals(terminal.getType())) {
      return;
    }
    synchronized (statusBar.terminalWriteLock) {
      writer.print(String.format("\033[%d;1H", statusBar.getScrollBottom()));
      writer.println();
      // Light gray background (256-color 237), bright white prompt text
      writer.println(
          new AttributedStringBuilder()
              .style(
                  AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold().background(236))
              .append("  \u276f " + input + "  ")
              .toAnsi());
      writer.flush();
    }
  }

  /**
   * Moves the cursor to the bottom of the scroll region so content output appends correctly within
   * the scrollable area.
   */
  private void moveToScrollBottom() {
    if ("dumb".equals(terminal.getType())) {
      return;
    }
    int scrollBottom = statusBar.getScrollBottom();
    writer.print(String.format("\033[%d;1H", scrollBottom));
    writer.flush();
  }

  /** Hides the terminal cursor (used during turn execution). */
  private void hideCursor() {
    if (!"dumb".equals(terminal.getType())) {
      writer.print("\033[?25l");
      writer.flush();
    }
  }

  /** Shows the terminal cursor (used before readLine). */
  private void showCursor() {
    if (!"dumb".equals(terminal.getType())) {
      writer.print("\033[?25h");
      writer.flush();
    }
  }

  private String buildPrompt() {
    return new AttributedStringBuilder()
        .style(AttributedStyle.DEFAULT)
        .append("  ")
        .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold())
        .append("\u276f ")
        .style(AttributedStyle.DEFAULT)
        .toAnsi(terminal);
  }

  /** Handles slash commands. Returns true if the command was recognized and handled. */
  private boolean handleSlashCommand(String command) {
    switch (command) {
      case "/help" -> {
        writer.println();
        writer.println(
            new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.bold())
                .append("Available commands:")
                .toAnsi());
        writer.println("  /help     List available commands");
        writer.println("  /clear    Clear the screen");
        writer.println("  /expand   Expand collapsed response");
        writer.println("  /exit     Exit Ganglia");
        writer.println();
        writer.println(
            new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.bold())
                .append("Keyboard shortcuts:")
                .toAnsi());
        writer.println("  Ctrl+C    Interrupt current turn");
        writer.println("  Ctrl+O    Expand collapsed response");
        writer.println("  Ctrl+E    View last tool output");
        writer.println("  Ctrl+D    Exit");
        writer.println();
        writer.flush();
        return true;
      }
      case "/clear" -> {
        writer.print("\033[2J\033[H");
        writer.flush();
        // Re-setup scroll region after clear
        statusBar.enable();
        statusBar.recalculateLayout();
        return true;
      }
      case "/expand" -> {
        eventRenderer.toggleLastResponseInPlace();
        return true;
      }
      case "/exit" -> {
        running = false;
        return true;
      }
      default -> {
        writer.println("Unknown command: " + command + ". Type /help for available commands.");
        writer.flush();
        return true;
      }
    }
  }

  private boolean consumeSlashMenuRequest() {
    if (slashMenuRequested) {
      slashMenuRequested = false;
      return true;
    }
    return false;
  }

  private boolean consumeDetailViewRequest() {
    if (detailViewRequested) {
      detailViewRequested = false;
      return true;
    }
    return false;
  }

  private void handleCtrlC() {
    AgentSignal signal = currentSignal.get();
    if (signal != null && !signal.isAborted()) {
      signal.abort();
      writer.println();
      writer.println("[Interrupted]");
      writer.flush();
    } else {
      writer.println("^C");
      writer.flush();
    }
  }

  private void executeTurn(String input) {
    moveToInputRow();

    CompletableFuture<Void> turnDone = new CompletableFuture<>();
    AgentSignal signal = new AgentSignal();
    currentSignal.set(signal);

    // Install INT signal handler so Ctrl+C during turn execution
    // aborts the turn instead of killing the process
    Terminal.SignalHandler prevHandler =
        terminal.handle(
            Terminal.Signal.INT,
            sig -> {
              handleCtrlC();
            });

    vertx.runOnContext(
        v -> {
          ganglia
              .sessionManager()
              .getSession(sessionId)
              .compose(context -> ganglia.agentLoop().run(input, context, signal))
              .onComplete(
                  ar -> {
                    currentSignal.set(null);
                    if (ar.failed() && ar.cause() instanceof AgentAbortedException) {
                      writer.println("\n[Turn aborted]");
                      writer.flush();
                    }
                    turnDone.complete(null);
                  });
        });

    try {
      turnDone.get();
    } catch (Exception e) {
      writer.println("\nError: " + e.getMessage());
      writer.flush();
    } finally {
      // Restore previous handler so JLine's readLine() handles Ctrl+C normally
      terminal.handle(Terminal.Signal.INT, prevHandler);
    }
  }

  public String getSessionId() {
    return sessionId;
  }

  public Terminal getTerminal() {
    return terminal;
  }

  public StatusBar getStatusBar() {
    return statusBar;
  }

  public EventRenderer getEventRenderer() {
    return eventRenderer;
  }

  @Override
  public void close() throws Exception {
    running = false;
    showCursor();
    statusBar.disable();
    if (terminal != null) {
      terminal.close();
    }
  }
}
