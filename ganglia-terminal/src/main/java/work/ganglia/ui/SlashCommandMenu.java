package work.ganglia.ui;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Custom slash command popup menu that renders above the input box within the scroll region. Unlike
 * JLine's built-in completion menu, this respects the custom scroll region layout managed by {@link
 * StatusBar}.
 *
 * <p>Layout when visible:
 *
 * <pre>
 *   [scroll region content...]
 *      /help      List available commands     ← highlighted
 *      /expand    Toggle expand/collapse
 *      /clear     Clear the screen
 *      /exit      Exit Ganglia
 *   ──────────────────────────────────────     ← input top border
 *   ❯ /                                       ← input line
 *   ──────────────────────────────────────     ← input bottom border
 *   ✓ Ready                                   ← status text
 * </pre>
 */
public class SlashCommandMenu {

  /** A slash command definition: name, description, and group. */
  public record CommandDef(String name, String description, String group) {}

  /** All available slash commands displayed in the menu. */
  static final List<CommandDef> COMMANDS =
      List.of(
          new CommandDef("/help", "List available commands", "Info"),
          new CommandDef("/expand", "Toggle expand/collapse", "Display"),
          new CommandDef("/clear", "Clear the screen", "Session"),
          new CommandDef("/exit", "Exit Ganglia", "Session"));

  private final Terminal terminal;
  private final PrintWriter writer;
  private final StatusBar statusBar;

  public SlashCommandMenu(Terminal terminal, StatusBar statusBar) {
    this.terminal = terminal;
    this.writer = terminal.writer();
    this.statusBar = statusBar;
  }

  /**
   * Shows the slash command menu and blocks until the user selects a command or cancels.
   *
   * @return the selected command string (e.g. "/help"), or null if cancelled
   */
  public String show() {
    int menuSize = COMMANDS.size();
    int selected = 0;

    // Enter raw mode so arrow keys are delivered as individual bytes
    // instead of being echoed as literal "^[[A" sequences.
    Attributes savedAttrs = terminal.getAttributes();
    Attributes rawAttrs = new Attributes(savedAttrs);
    rawAttrs.setLocalFlag(Attributes.LocalFlag.ICANON, false);
    rawAttrs.setLocalFlag(Attributes.LocalFlag.ECHO, false);
    rawAttrs.setInputFlag(Attributes.InputFlag.ICRNL, false);
    rawAttrs.setControlChar(Attributes.ControlChar.VMIN, 1);
    rawAttrs.setControlChar(Attributes.ControlChar.VTIME, 0);
    terminal.setAttributes(rawAttrs);

    // Render menu initially
    renderMenu(selected, menuSize);

    try {
      // Read keystrokes directly from the terminal
      var termReader = terminal.reader();
      while (true) {
        int c = termReader.read();
        if (c == -1) {
          break;
        }

        if (c == 27) { // ESC sequence
          // Check for arrow keys: ESC [ A/B
          int next = termReader.read();
          if (next == '[') {
            int arrow = termReader.read();
            if (arrow == 'A') { // Up
              selected = (selected - 1 + menuSize) % menuSize;
              renderMenu(selected, menuSize);
              continue;
            } else if (arrow == 'B') { // Down
              selected = (selected + 1) % menuSize;
              renderMenu(selected, menuSize);
              continue;
            }
            // Other escape sequences — ignore
            continue;
          }
          // Plain ESC — cancel
          clearMenu(menuSize);
          return null;
        }

        if (c == 'q' || c == 'Q') {
          clearMenu(menuSize);
          return null;
        }

        if (c == '\r' || c == '\n') {
          clearMenu(menuSize);
          return COMMANDS.get(selected).name();
        }

        if (c == 'k' || c == 'K') { // vim up
          selected = (selected - 1 + menuSize) % menuSize;
          renderMenu(selected, menuSize);
        } else if (c == 'j' || c == 'J') { // vim down
          selected = (selected + 1) % menuSize;
          renderMenu(selected, menuSize);
        }
      }
    } catch (IOException e) {
      // Terminal read failure — cancel silently
    } finally {
      terminal.setAttributes(savedAttrs);
    }

    clearMenu(menuSize);
    return null;
  }

  /**
   * Renders (or re-renders) the menu items above the input box. Each item occupies one row in the
   * scroll region, positioned just above the reserved bottom panel.
   */
  private void renderMenu(int selectedIndex, int menuSize) {
    synchronized (statusBar.terminalWriteLock) {
      // Menu rows sit at the bottom of the scroll region, just above the reserved area.
      // scrollBottom is the last row of the scroll region.
      int scrollBottom = statusBar.getScrollBottom();
      int menuStartRow = scrollBottom - menuSize + 1;

      int termWidth = terminal.getWidth();
      int nameColWidth = 12; // enough for "/expand" + padding

      for (int i = 0; i < menuSize; i++) {
        int row = menuStartRow + i;
        CommandDef cmd = COMMANDS.get(i);

        // Move to the row and clear it
        writer.print(AnsiCodes.moveAndClear(row));

        AttributedStringBuilder asb = new AttributedStringBuilder();
        if (i == selectedIndex) {
          // Selected: dark background (236) + bold bright white
          AttributedStyle selStyle =
              AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold().background(236);
          String line = formatMenuLine(cmd, nameColWidth, termWidth);
          asb.style(selStyle).append(line);
        } else {
          // Unselected: dim/faint text
          AttributedStyle dimStyle = AttributedStyle.DEFAULT.faint();
          String line = formatMenuLine(cmd, nameColWidth, termWidth);
          asb.style(dimStyle).append(line);
        }

        writer.print(asb.toAnsi(terminal));
      }

      // Park cursor at the input row
      statusBar.parkCursorAtInput();
      writer.flush();
    }
  }

  /** Formats a single menu line: " /command Description" padded to terminal width. */
  private String formatMenuLine(CommandDef cmd, int nameColWidth, int termWidth) {
    String name = cmd.name();
    String desc = cmd.description();
    StringBuilder sb = new StringBuilder();
    sb.append("   ");
    sb.append(name);
    // Pad name column
    int padding = nameColWidth - name.length();
    if (padding > 0) {
      sb.append(" ".repeat(padding));
    }
    sb.append(desc);
    // Pad to terminal width so background fills the whole line
    int totalLen = sb.length();
    if (totalLen < termWidth) {
      sb.append(" ".repeat(termWidth - totalLen));
    }
    return sb.toString();
  }

  /** Clears the menu rows, restoring them to blank scroll region lines. */
  private void clearMenu(int menuSize) {
    synchronized (statusBar.terminalWriteLock) {
      int scrollBottom = statusBar.getScrollBottom();
      int menuStartRow = scrollBottom - menuSize + 1;

      for (int i = 0; i < menuSize; i++) {
        int row = menuStartRow + i;
        writer.print(AnsiCodes.moveAndClear(row));
      }

      statusBar.parkCursorAtInput();
      writer.flush();
    }
  }
}
