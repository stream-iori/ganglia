package me.stream.ganglia.ui;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.Visitor;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

/**
 * A simple Markdown to ANSI renderer using Flexmark.
 */
public class MarkdownRenderer {

    private final Parser parser;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
    }

    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) return "";
        Node document = parser.parse(markdown);
        AttributedStringBuilder sb = new AttributedStringBuilder();
        renderNode(document, sb, AttributedStyle.DEFAULT);
        return sb.toAnsi();
    }

    private void renderNode(Node node, AttributedStringBuilder sb, AttributedStyle style) {
        if (node instanceof Text) {
            sb.style(style).append(node.getChars().toString());
        } else if (node instanceof StrongEmphasis) {
            renderChildren(node, sb, style.bold());
        } else if (node instanceof Emphasis) {
            renderChildren(node, sb, style.italic());
        } else if (node instanceof Code) {
            sb.style(style.foreground(AttributedStyle.YELLOW).background(AttributedStyle.BLACK + 8))
              .append(" ")
              .append(node.getChildren().iterator().next().getChars().toString())
              .append(" ");
        } else if (node instanceof FencedCodeBlock) {
            sb.append("\n");
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("--- Code Block ---")
              .append("\n");
            sb.style(style.foreground(AttributedStyle.WHITE))
              .append(((FencedCodeBlock) node).getContentChars().toString());
            sb.style(style.foreground(AttributedStyle.BRIGHT + AttributedStyle.YELLOW))
              .append("------------------")
              .append("\n");
        } else if (node instanceof Heading) {
            sb.append("\n");
            int level = ((Heading) node).getLevel();
            AttributedStyle headingStyle = style.bold().foreground(AttributedStyle.CYAN);
            if (level == 1) headingStyle = headingStyle.underline();
            sb.style(headingStyle);
            renderChildren(node, sb, headingStyle);
            sb.append("\n");
        } else if (node instanceof BulletList) {
            renderChildren(node, sb, style);
        } else if (node instanceof BulletListItem) {
            sb.style(style).append("  • ");
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
        for (Node child : node.getChildren()) {
            renderNode(child, sb, style);
        }
    }
}
