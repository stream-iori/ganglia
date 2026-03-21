package work.ganglia.ui;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.io.PrintWriter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.external.tool.ObservationEvent;

/** Enhanced Terminal UI using JLine 3 for rich output and reactive streaming. */
public class TerminalUI implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(TerminalUI.class);
  private final Vertx vertx;
  private final Terminal terminal;
  private final PrintWriter writer;
  private final MarkdownRenderer markdownRenderer;
  private final StringBuilder accumulatedContent = new StringBuilder();

  public TerminalUI(Vertx vertx, Terminal terminal) {
    this.vertx = vertx;
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.markdownRenderer = new MarkdownRenderer();
  }

  /**
   * Factory method to create and initialize a TerminalUI. Handles JLine terminal initialization
   * with fallback to dumb terminal.
   */
  public static TerminalUI create(Vertx vertx) {
    Terminal terminal;
    try {
      terminal = TerminalBuilder.builder().system(true).build();
    } catch (IOException e) {
      logger.warn("Failed to initialize JLine terminal, falling back to dummy", e);
      try {
        terminal = TerminalBuilder.builder().dumb(true).build();
      } catch (IOException ex) {
        throw new RuntimeException("Critical failure initializing terminal", ex);
      }
    }
    return new TerminalUI(vertx, terminal);
  }

  public Terminal getTerminal() {
    return terminal;
  }

  /** Subscribes to the event bus for observations and prints them to the terminal. */
  public MessageConsumer<JsonObject> listenToStream(String sessionId) {
    String address = "ganglia.observations." + sessionId;
    return vertx
        .eventBus()
        .consumer(
            address,
            message -> {
              ObservationEvent event = message.body().mapTo(ObservationEvent.class);
              renderEvent(event);
            });
  }

  private void renderEvent(ObservationEvent event) {
    switch (event.type()) {
      case TURN_STARTED:
        accumulatedContent.setLength(0);
        break;
      case TOKEN_RECEIVED:
        if (event.content() != null) {
          accumulatedContent.append(event.content());
          writer.print(event.content());
          writer.flush();
        }
        break;
      case TOOL_STARTED:
        writer.println();
        AttributedStringBuilder asb =
            new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append("[Tool: ")
                .append(event.content())
                .append("]");

        if (event.data() != null && !event.data().isEmpty()) {
          if ("run_shell_command".equals(event.content()) && event.data().containsKey("command")) {
            asb.append(" ")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).italic())
                .append((String) event.data().get("command"));
          } else {
            asb.append(" ")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).italic())
                .append(event.data().toString());
          }
        }
        writer.println(asb.toAnsi());
        writer.flush();
        break;
      case TOOL_FINISHED:
        if (event.content() != null && event.content().startsWith("Error:")) {
          writer.println(
              new AttributedStringBuilder()
                  .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED))
                  .append("[✗ Failed] ")
                  .append(event.content())
                  .toAnsi());
        } else {
          writer.println(
              new AttributedStringBuilder()
                  .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                  .append("[✓ Done]")
                  .toAnsi());
        }
        writer.flush();
        break;
      case ERROR:
        writer.println();
        writer.print(markdownRenderer.render("### ERROR\n" + event.content()));
        writer.flush();
        break;
      case TURN_FINISHED:
        writer.println();
        writer.flush();
        break;
      case REASONING_STARTED:
        writer.print(
            new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).italic())
                .append("Thinking... ")
                .toAnsi());
        writer.flush();
        break;
      default:
        break;
    }
  }

  public void printMarkdown(String markdown) {
    writer.println(markdownRenderer.render(markdown));
    writer.flush();
  }

  @Override
  public void close() throws Exception {
    if (terminal != null) {
      terminal.close();
    }
  }
}
