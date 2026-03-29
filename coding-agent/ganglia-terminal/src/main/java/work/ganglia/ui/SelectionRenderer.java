package work.ganglia.ui;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jline.reader.LineReader;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * Renders {@code ask_selection} questions interactively in the terminal and collects user answers
 * via JLine.
 *
 * <p>For each question:
 *
 * <ul>
 *   <li>{@code choice} — numbered list of options; user types number(s) separated by commas
 *   <li>{@code yesno} — user types {@code y} or {@code n}
 *   <li>{@code text} — free-form input with optional placeholder hint
 * </ul>
 */
public class SelectionRenderer {

  private static final AttributedStyle HEADER_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN).bold();
  private static final AttributedStyle QUESTION_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).bold();
  private static final AttributedStyle OPTION_INDEX_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW).bold();
  private static final AttributedStyle OPTION_LABEL_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
  private static final AttributedStyle OPTION_DESC_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();
  private static final AttributedStyle PROMPT_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN).bold();
  private static final AttributedStyle HINT_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();

  private final PrintWriter writer;
  private final LineReader reader;
  private final StatusBar statusBar;

  public SelectionRenderer(PrintWriter writer, LineReader reader, StatusBar statusBar) {
    this.writer = writer;
    this.reader = reader;
    this.statusBar = statusBar;
  }

  /**
   * Renders all questions and collects answers. Returns a newline-separated string of answers, one
   * per question, suitable as the tool output fed back to the agent via {@code resume()}.
   */
  @SuppressWarnings("unchecked")
  public String renderAndCollect(List<Map<String, Object>> questions) {
    List<String> answers = new ArrayList<>();

    synchronized (statusBar.terminalWriteLock) {
      writer.println();
      writer.flush();
    }

    for (Map<String, Object> question : questions) {
      String type = (String) question.getOrDefault("type", "text");
      String answer =
          switch (type) {
            case "choice" -> renderChoice(question);
            case "yesno" -> renderYesNo(question);
            default -> renderText(question);
          };
      answers.add(answer);
    }

    return String.join("\n", answers);
  }

  @SuppressWarnings("unchecked")
  private String renderChoice(Map<String, Object> question) {
    String header = (String) question.getOrDefault("header", "");
    String questionText = (String) question.getOrDefault("question", "");
    boolean multiSelect = Boolean.TRUE.equals(question.get("multiSelect"));
    List<Map<String, Object>> options =
        (List<Map<String, Object>>) question.getOrDefault("options", List.of());

    synchronized (statusBar.terminalWriteLock) {
      // Header chip
      if (!header.isEmpty()) {
        writer.println(
            new AttributedStringBuilder()
                .style(HEADER_STYLE)
                .append("  [" + header + "]")
                .toAnsi());
      }
      // Question text
      writer.println(
          new AttributedStringBuilder().style(QUESTION_STYLE).append("  " + questionText).toAnsi());
      writer.println();

      // Options
      for (int i = 0; i < options.size(); i++) {
        Map<String, Object> opt = options.get(i);
        String label = String.valueOf(opt.getOrDefault("label", ""));
        String desc = String.valueOf(opt.getOrDefault("description", ""));

        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(OPTION_INDEX_STYLE).append("  " + (i + 1) + ". ");
        sb.style(OPTION_LABEL_STYLE).append(label);
        if (!desc.isEmpty()) {
          sb.style(OPTION_DESC_STYLE).append("  — " + desc);
        }
        writer.println(sb.toAnsi());
      }
      writer.println();

      // Prompt hint
      String hint =
          multiSelect ? "(enter numbers separated by commas, e.g. 1,3)" : "(enter a number)";
      writer.print(
          new AttributedStringBuilder().style(HINT_STYLE).append("  " + hint + " ").toAnsi());
      writer.flush();
    }

    // Collect input outside the lock so the reader can write to terminal freely
    String input = safeReadLine(buildSelectionPrompt());

    // Map numbers to option values
    return resolveChoiceValues(input, options, multiSelect);
  }

  private String renderYesNo(Map<String, Object> question) {
    String header = (String) question.getOrDefault("header", "");
    String questionText = (String) question.getOrDefault("question", "");

    synchronized (statusBar.terminalWriteLock) {
      if (!header.isEmpty()) {
        writer.println(
            new AttributedStringBuilder()
                .style(HEADER_STYLE)
                .append("  [" + header + "]")
                .toAnsi());
      }
      writer.println(
          new AttributedStringBuilder().style(QUESTION_STYLE).append("  " + questionText).toAnsi());
      writer.println();
      writer.print(new AttributedStringBuilder().style(HINT_STYLE).append("  (y/n) ").toAnsi());
      writer.flush();
    }

    String input = safeReadLine(buildSelectionPrompt()).trim().toLowerCase();
    return input.startsWith("y") ? "yes" : "no";
  }

  private String renderText(Map<String, Object> question) {
    String header = (String) question.getOrDefault("header", "");
    String questionText = (String) question.getOrDefault("question", "");
    String placeholder = (String) question.getOrDefault("placeholder", "");

    synchronized (statusBar.terminalWriteLock) {
      if (!header.isEmpty()) {
        writer.println(
            new AttributedStringBuilder()
                .style(HEADER_STYLE)
                .append("  [" + header + "]")
                .toAnsi());
      }
      writer.println(
          new AttributedStringBuilder().style(QUESTION_STYLE).append("  " + questionText).toAnsi());
      if (!placeholder.isEmpty()) {
        writer.println(
            new AttributedStringBuilder()
                .style(HINT_STYLE)
                .append("  (" + placeholder + ")")
                .toAnsi());
      }
      writer.println();
      writer.flush();
    }

    return safeReadLine(buildSelectionPrompt()).trim();
  }

  private String buildSelectionPrompt() {
    return new AttributedStringBuilder()
        .style(PROMPT_STYLE)
        .append("  \u276f ")
        .style(AttributedStyle.DEFAULT)
        .toAnsi();
  }

  private String safeReadLine(String prompt) {
    try {
      return reader.readLine(prompt);
    } catch (Exception e) {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  private String resolveChoiceValues(
      String input, List<Map<String, Object>> options, boolean multiSelect) {
    if (input == null || input.trim().isEmpty()) {
      return "";
    }
    String[] parts = input.split(",");
    List<String> selected = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      try {
        int idx = Integer.parseInt(trimmed) - 1;
        if (idx >= 0 && idx < options.size()) {
          Object value = options.get(idx).get("value");
          if (value != null) {
            selected.add(value.toString());
          } else {
            selected.add(String.valueOf(options.get(idx).getOrDefault("label", trimmed)));
          }
        }
      } catch (NumberFormatException e) {
        // Treat as raw value if not a number
        selected.add(trimmed);
      }
      if (!multiSelect) {
        break;
      }
    }
    return String.join(", ", selected);
  }
}
