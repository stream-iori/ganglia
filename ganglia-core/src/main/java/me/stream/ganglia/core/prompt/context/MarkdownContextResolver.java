package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Vertx;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves context fragments from Markdown files by parsing H2 headers using CommonMark.
 */
public class MarkdownContextResolver {
    private final Vertx vertx;
    private final Parser parser;
    private static final Pattern HEADER_META_PATTERN = Pattern.compile("\\[(.+?)\\](?:\\s+\\(Priority:\\s+(\\d+)(?:,\\s+(Mandatory))?\\))?");

    public MarkdownContextResolver(Vertx vertx) {
        this.vertx = vertx;
        this.parser = Parser.builder()
                .extensions(Collections.singletonList(TablesExtension.create()))
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build();
    }

    public List<ContextFragment> parse(String sourceName, String content) {
        Node document = parser.parse(content);
        List<ContextFragment> fragments = new ArrayList<>();
        
        Node child = document.getFirstChild();
        String currentName = null;
        int currentPriority = 5;
        boolean currentMandatory = false;
        int fragmentStart = -1;
        int fragmentEnd = -1;

        while (child != null) {
            if (child instanceof Heading && ((Heading) child).getLevel() == 2) {
                // If we were tracking a fragment, save it
                if (currentName != null && fragmentStart != -1) {
                    fragments.add(new ContextFragment(currentName, content.substring(fragmentStart, fragmentEnd).trim(), currentPriority, currentMandatory));
                }
                
                // Parse new H2
                String headerText = getHeaderText((Heading) child);
                Matcher matcher = HEADER_META_PATTERN.matcher(headerText);
                
                if (matcher.find()) {
                    currentName = matcher.group(1);
                    String priorityStr = matcher.group(2);
                    currentPriority = (priorityStr != null) ? Integer.parseInt(priorityStr) : 5;
                    currentMandatory = matcher.group(3) != null;
                } else {
                    currentName = headerText;
                    currentPriority = 5;
                    currentMandatory = false;
                }
                
                fragmentStart = -1;
                fragmentEnd = -1;
            } else if (currentName != null) {
                // Collect content for current H2
                List<SourceSpan> spans = child.getSourceSpans();
                if (spans != null && !spans.isEmpty()) {
                    // SourceSpans in commonmark-java provide line and column, but we need character offsets.
                    // However, for Block nodes with IncludeSourceSpans.BLOCKS, 
                    // some implementations of CommonMark provide character offsets in specific attributes
                    // or we have to calculate them.
                    
                    // Actually, a much simpler and more robust way in CommonMark 
                    // when we want to preserve original markdown between headers 
                    // is to just use the end of the header and the start of the next header 
                    // from the original string, using the parser just to find the header positions.
                    
                    // Let's re-evaluate: CommonMark's Heading node DOES NOT easily 
                    // give character offsets in the original string without more complex setup.
                }
            }
            child = child.getNext();
        }

        // The regex approach was actually quite good for "slicing", 
        // but it lacked proper markdown understanding (e.g. headers inside code blocks).
        // Let's use CommonMark to find the exact character offsets of H2 headers.
        
        return parseWithOffsets(content);
    }

    private List<ContextFragment> parseWithOffsets(String content) {
        Node document = parser.parse(content);
        List<HeaderInfo> headers = new ArrayList<>();
        
        Node child = document.getFirstChild();
        while (child != null) {
            if (child instanceof Heading && ((Heading) child).getLevel() == 2) {
                // We use a trick: the parser doesn't give offsets, but we can find this header 
                // in the original string by its content, starting from the last found position.
                headers.add(new HeaderInfo(getHeaderText((Heading) child), child));
            }
            child = child.getNext();
        }
        
        List<ContextFragment> fragments = new ArrayList<>();
        int lastPos = 0;
        for (int i = 0; i < headers.size(); i++) {
            HeaderInfo current = headers.get(i);
            // Find where this H2 starts in the original string
            // We search for "## " + title
            String searchStr = "## " + getOriginalHeaderLine(content, lastPos, current.title);
            int start = content.indexOf(searchStr, lastPos);
            if (start == -1) continue; // Should not happen
            
            // If there was a previous header, its content ends here
            if (!fragments.isEmpty()) {
                // Update previous fragment content
                int prevIdx = fragments.size() - 1;
                ContextFragment prev = fragments.get(prevIdx);
                String fragContent = content.substring(lastPos, start).trim();
                fragments.set(prevIdx, new ContextFragment(prev.name(), fragContent, prev.priority(), prev.isMandatory()));
            }
            
            // Prepare current fragment
            Matcher matcher = HEADER_META_PATTERN.matcher(current.title);
            String name;
            int priority = 5;
            boolean mandatory = false;
            
            if (matcher.find()) {
                name = matcher.group(1);
                String priorityStr = matcher.group(2);
                priority = (priorityStr != null) ? Integer.parseInt(priorityStr) : 5;
                mandatory = matcher.group(3) != null;
            } else {
                name = current.title;
            }
            
            // The content starts after the header line
            int nextNewLine = content.indexOf("\n", start);
            lastPos = (nextNewLine == -1) ? content.length() : nextNewLine + 1;
            
            fragments.add(new ContextFragment(name, "", priority, mandatory));
        }
        
        // Final content
        if (!fragments.isEmpty()) {
            int prevIdx = fragments.size() - 1;
            ContextFragment prev = fragments.get(prevIdx);
            String fragContent = content.substring(lastPos).trim();
            fragments.set(prevIdx, new ContextFragment(prev.name(), fragContent, prev.priority(), prev.isMandatory()));
        }
        
        return fragments;
    }

    private String getOriginalHeaderLine(String content, int startFrom, String title) {
        // The title might have been normalized by commonmark (e.g. entities resolved).
        // But for our purpose, we expect it to be mostly literal.
        return title; 
    }

    private String getHeaderText(Heading heading) {
        StringBuilder sb = new StringBuilder();
        Node child = heading.getFirstChild();
        while (child != null) {
            if (child instanceof Text) {
                sb.append(((Text) child).getLiteral());
            } else if (child instanceof Code) {
                sb.append(((Code) child).getLiteral());
            }
            child = child.getNext();
        }
        return sb.toString();
    }
    
    private record HeaderInfo(String title, Node node) {}
}