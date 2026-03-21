package work.ganglia.infrastructure.internal.prompt.context;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import work.ganglia.port.internal.prompt.ContextFragment;

/**
 * Resolves context fragments from Markdown files by parsing H2 headers. Extracts metadata like
 * name, priority, and mandatory status from headers using the format: {@code ## [Name] (Priority:
 * 5, Mandatory)}
 */
public class MarkdownContextResolver {
  private final Vertx vertx;
  private final Parser parser;

  private static final Pattern HEADER_META_PATTERN =
      Pattern.compile(
          "\\[(?<name>.+?)\\](?:\\s+\\(Priority:\\s+(?<priority>\\d+)(?:,\\s+(?<mandatory>Mandatory))?\\))?");

  public MarkdownContextResolver(Vertx vertx) {
    this.vertx = vertx;
    this.parser =
        Parser.builder()
            .extensions(Collections.singletonList(TablesExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();
  }

  /**
   * Parses the markdown content into a list of {@link ContextFragment}s asynchronously. Each H2
   * header starts a new fragment. Everything before the first H2 header is ignored. This method
   * uses {@code vertx.executeBlocking} to avoid blocking the EventLoop.
   *
   * @param sourceName The name of the source (e.g., file path), used for logging/debugging.
   * @param content The markdown content to parse.
   * @return A future that completes with the list of context fragments found in the content.
   */
  public Future<List<ContextFragment>> parse(String sourceName, String content) {
    if (content == null || content.isBlank()) {
      return Future.succeededFuture(Collections.emptyList());
    }

    return vertx.executeBlocking(() -> parseSync(content), false);
  }

  private List<ContextFragment> parseSync(String content) {
    Node document = parser.parse(content);
    List<H2Header> h2Headers = findH2Headers(document);

    if (h2Headers.isEmpty()) {
      return Collections.emptyList();
    }

    int[] lineOffsets = calculateLineOffsets(content);
    List<ContextFragment> fragments = new ArrayList<>();

    for (int i = 0; i < h2Headers.size(); i++) {
      H2Header current = h2Headers.get(i);
      int nextHeaderLine = (i + 1 < h2Headers.size()) ? h2Headers.get(i + 1).lineIndex() : -1;

      int contentStart = getLineStartOffset(current.lineIndex() + 1, lineOffsets, content.length());
      int contentEnd = (nextHeaderLine != -1) ? lineOffsets[nextHeaderLine] : content.length();

      String fragmentContent = content.substring(contentStart, contentEnd).trim();
      HeaderMetadata meta = parseMetadata(current.title());

      fragments.add(
          new ContextFragment(meta.name(), fragmentContent, meta.priority(), meta.mandatory()));
    }

    return fragments;
  }

  private List<H2Header> findH2Headers(Node document) {
    List<H2Header> headers = new ArrayList<>();
    Node child = document.getFirstChild();
    while (child != null) {
      if (child instanceof Heading heading && heading.getLevel() == 2) {
        List<SourceSpan> spans = heading.getSourceSpans();
        if (spans != null && !spans.isEmpty()) {
          headers.add(new H2Header(getHeaderText(heading), spans.get(0).getLineIndex()));
        }
      }
      child = child.getNext();
    }
    return headers;
  }

  private int getLineStartOffset(int lineIndex, int[] lineOffsets, int maxLength) {
    if (lineIndex < 0) return 0;
    if (lineIndex >= lineOffsets.length) return maxLength;
    return lineOffsets[lineIndex];
  }

  private int[] calculateLineOffsets(String content) {
    List<Integer> offsets = new ArrayList<>();
    offsets.add(0);
    for (int i = 0; i < content.length(); i++) {
      if (content.charAt(i) == '\n') {
        offsets.add(i + 1);
      }
    }
    return offsets.stream().mapToInt(Integer::intValue).toArray();
  }

  private String getHeaderText(Heading heading) {
    StringBuilder sb = new StringBuilder();
    appendNodeText(heading, sb);
    return sb.toString().trim();
  }

  private void appendNodeText(Node node, StringBuilder sb) {
    Node child = node.getFirstChild();
    while (child != null) {
      if (child instanceof Text text) {
        sb.append(text.getLiteral());
      } else if (child instanceof Code code) {
        sb.append(code.getLiteral());
      } else {
        appendNodeText(child, sb);
      }
      child = child.getNext();
    }
  }

  private HeaderMetadata parseMetadata(String headerText) {
    Matcher matcher = HEADER_META_PATTERN.matcher(headerText);
    if (matcher.find()) {
      String name = matcher.group("name");
      String priorityStr = matcher.group("priority");
      int priority = (priorityStr != null) ? Integer.parseInt(priorityStr) : 5;
      boolean mandatory = matcher.group("mandatory") != null;
      return new HeaderMetadata(name, priority, mandatory);
    }
    return new HeaderMetadata(headerText, 5, false);
  }

  private record H2Header(String title, int lineIndex) {}

  private record HeaderMetadata(String name, int priority, boolean mandatory) {}
}
