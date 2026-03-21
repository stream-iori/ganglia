package work.ganglia.ui;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/** A simple Markdown to ANSI renderer using CommonMark. */
public class MarkdownRenderer {

  private static final AttributedStyle CODE_BG =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).background(236);
  private static final AttributedStyle CODE_LABEL = AttributedStyle.DEFAULT.faint().background(236);
  private static final AttributedStyle INLINE_CODE =
      AttributedStyle.DEFAULT
          .foreground(AttributedStyle.YELLOW)
          .background(AttributedStyle.BLACK + 8);
  private static final AttributedStyle CHECKED_STYLE =
      AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
  private static final AttributedStyle CHECKED_TEXT_STYLE = CHECKED_STYLE.faint();

  private final Parser parser;

  public MarkdownRenderer() {
    this.parser = Parser.builder().build();
  }

  public String render(String markdown) {
    if (markdown == null || markdown.isEmpty()) return "";
    Node document = parser.parse(markdown);
    AttributedStringBuilder sb = new AttributedStringBuilder();
    renderNode(document, sb, AttributedStyle.DEFAULT);
    return sb.toAnsi();
  }

  private void renderNode(Node node, AttributedStringBuilder sb, AttributedStyle style) {
    if (node instanceof Text text) {
      sb.style(style).append(text.getLiteral());
    } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
      sb.append("\n");
    } else if (node instanceof StrongEmphasis) {
      renderChildren(node, sb, style.bold());
    } else if (node instanceof Emphasis) {
      renderChildren(node, sb, style.italic());
    } else if (node instanceof Code code) {
      sb.style(INLINE_CODE).append(" ").append(code.getLiteral()).append(" ");
    } else if (node instanceof FencedCodeBlock fenced) {
      renderCodeBlock(sb, fenced.getLiteral(), fenced.getInfo());
    } else if (node instanceof IndentedCodeBlock indented) {
      renderCodeBlock(sb, indented.getLiteral(), null);
    } else if (node instanceof HtmlBlock htmlBlock) {
      sb.style(style).append(htmlBlock.getLiteral());
    } else if (node instanceof HtmlInline htmlInline) {
      sb.style(style).append(htmlInline.getLiteral());
    } else if (node instanceof Heading heading) {
      renderHeading(heading, sb, style);
    } else if (node instanceof BulletList || node instanceof OrderedList) {
      renderChildren(node, sb, style);
    } else if (node instanceof ListItem listItem) {
      renderListItem(listItem, sb, style);
    } else if (node instanceof Paragraph) {
      renderChildren(node, sb, style);
      sb.append("\n");
    } else {
      renderChildren(node, sb, style);
    }
  }

  private void renderHeading(Heading heading, AttributedStringBuilder sb, AttributedStyle style) {
    sb.append("\n");
    int level = heading.getLevel();
    AttributedStyle headingStyle = style.bold().foreground(AttributedStyle.CYAN);
    if (level == 1) {
      headingStyle = headingStyle.underline();
    }
    String prefix = level <= 1 ? "" : "#".repeat(level) + " ";
    sb.style(headingStyle).append(prefix);
    renderChildren(heading, sb, headingStyle);
    sb.append("\n");
  }

  private void renderListItem(
      ListItem listItem, AttributedStringBuilder sb, AttributedStyle style) {
    if (listItem.getParent() instanceof OrderedList) {
      int index = 1;
      Node prev = listItem.getPrevious();
      while (prev != null) {
        index++;
        prev = prev.getPrevious();
      }
      sb.style(style).append("  " + index + ". ");
      renderChildren(listItem, sb, style);
    } else {
      CheckboxResult checkbox = detectCheckbox(listItem);
      if (checkbox != null) {
        renderCheckboxItem(listItem, sb, style, checkbox);
      } else {
        sb.style(style).append("  \u2022 ");
        renderChildren(listItem, sb, style);
      }
    }
    sb.append("\n");
  }

  private void renderCheckboxItem(
      ListItem listItem,
      AttributedStringBuilder sb,
      AttributedStyle style,
      CheckboxResult checkbox) {
    AttributedStyle textStyle;
    if (checkbox.checked) {
      sb.style(CHECKED_STYLE).append("  \u2611 ");
      textStyle = CHECKED_TEXT_STYLE;
    } else {
      sb.style(style).append("  \u2610 ");
      textStyle = style;
    }

    // Patch the first Text node to strip the checkbox prefix, then render normally
    Node paragraph = listItem.getFirstChild();
    Text firstText = (Text) ((Paragraph) paragraph).getFirstChild();
    String original = firstText.getLiteral();
    firstText.setLiteral(checkbox.remainingText);
    try {
      renderChildren(listItem, sb, textStyle);
    } finally {
      firstText.setLiteral(original);
    }
  }

  /**
   * Renders a code block with dark gray background (256-color 236). Shows an optional language
   * label on the first line.
   */
  private void renderCodeBlock(AttributedStringBuilder sb, String literal, String lang) {
    sb.append("\n");

    if (lang != null && !lang.isEmpty()) {
      sb.style(CODE_LABEL).append("  " + lang);
      sb.style(AttributedStyle.DEFAULT).append("\n");
    }

    String code = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
    for (String line : code.split("\n", -1)) {
      sb.style(CODE_BG).append("  " + line);
      sb.style(AttributedStyle.DEFAULT).append("\n");
    }
  }

  // ── Checkbox detection ──────────────────────────────────────────────

  private record CheckboxResult(boolean checked, String remainingText) {}

  /**
   * Detects checkbox syntax in a ListItem. CommonMark parses "- [ ] text" as ListItem > Paragraph >
   * Text("[ ] text"). Returns null if no checkbox found.
   */
  private CheckboxResult detectCheckbox(Node listItem) {
    Node paragraph = listItem.getFirstChild();
    if (!(paragraph instanceof Paragraph)) return null;
    Node firstChild = ((Paragraph) paragraph).getFirstChild();
    if (!(firstChild instanceof Text text)) return null;
    String lit = text.getLiteral();
    if (lit.startsWith("[ ] ") || "[ ]".equals(lit)) {
      return new CheckboxResult(false, lit.substring(3).stripLeading());
    } else if (lit.startsWith("[x] ")
        || lit.startsWith("[X] ")
        || "[x]".equals(lit)
        || "[X]".equals(lit)) {
      return new CheckboxResult(true, lit.substring(3).stripLeading());
    }
    return null;
  }

  // ── Tree traversal ──────────────────────────────────────────────────

  private void renderChildren(Node node, AttributedStringBuilder sb, AttributedStyle style) {
    Node child = node.getFirstChild();
    while (child != null) {
      renderNode(child, sb, style);
      child = child.getNext();
    }
  }
}
