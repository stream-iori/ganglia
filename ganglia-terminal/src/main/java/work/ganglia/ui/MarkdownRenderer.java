package work.ganglia.ui;

import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * A simple Markdown to ANSI renderer using CommonMark.
 */
public class MarkdownRenderer {

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
        } else if (node instanceof SoftLineBreak) {
            sb.append("\n");
        } else if (node instanceof HardLineBreak) {
            sb.append("\n");
        } else if (node instanceof StrongEmphasis) {
            renderChildren(node, sb, style.bold());
        } else if (node instanceof Emphasis) {
            renderChildren(node, sb, style.italic());
        } else if (node instanceof Code code) {
            sb.style(style.foreground(AttributedStyle.YELLOW).background(AttributedStyle.BLACK + 8))
              .append(" ")
              .append(code.getLiteral())
              .append(" ");
        } else if (node instanceof FencedCodeBlock fenced) {
            sb.append("\n");
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("--- Code Block ---")
              .append("\n");
            sb.style(style.foreground(AttributedStyle.WHITE))
              .append(fenced.getLiteral());
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("------------------")
              .append("\n");
        } else if (node instanceof IndentedCodeBlock indented) {
            sb.append("\n");
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("--- Code Block ---")
              .append("\n");
            sb.style(style.foreground(AttributedStyle.WHITE))
              .append(indented.getLiteral());
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("------------------")
              .append("\n");
        } else if (node instanceof HtmlBlock htmlBlock) {
            sb.style(style).append(htmlBlock.getLiteral());
        } else if (node instanceof HtmlInline htmlInline) {
            sb.style(style).append(htmlInline.getLiteral());
        } else if (node instanceof Heading heading) {
            sb.append("\n");
            int level = heading.getLevel();
            AttributedStyle headingStyle = style.bold().foreground(AttributedStyle.CYAN);
            if (level == 1) headingStyle = headingStyle.underline();
            sb.style(headingStyle);
            renderChildren(node, sb, headingStyle);
            sb.append("\n");
        } else if (node instanceof BulletList) {
            renderChildren(node, sb, style);
        } else if (node instanceof ListItem && node.getParent() instanceof BulletList) {
            sb.style(style).append("  \u2022 ");
            renderChildren(node, sb, style);
            sb.append("\n");
        } else if (node instanceof Paragraph) {
            renderChildren(node, sb, style);
            sb.append("\n");
        } else {
            renderChildren(node, sb, style);
        }
    }

    private void renderChildren(Node node, AttributedStringBuilder sb, AttributedStyle style) {
        Node child = node.getFirstChild();
        while (child != null) {
            renderNode(child, sb, style);
            child = child.getNext();
        }
    }
}
