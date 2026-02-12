package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Vertx;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves context fragments from Markdown files by parsing H2 headers.
 */
public class MarkdownContextResolver {
    private final Vertx vertx;
    private static final Pattern HEADER_PATTERN = Pattern.compile("^##\\s+\\[(.+?)\\](?:\\s+\\(Priority:\\s+(\\d+)(?:,\\s+(Mandatory))?\\))?", Pattern.MULTILINE);

    public MarkdownContextResolver(Vertx vertx) {
        this.vertx = vertx;
    }

    public List<ContextFragment> parse(String sourceName, String content) {
        List<ContextFragment> fragments = new ArrayList<>();
        Matcher matcher = HEADER_PATTERN.matcher(content);

        int lastEnd = 0;
        String currentName = null;
        int currentPriority = 5;
        boolean currentMandatory = false;

        while (matcher.find()) {
            if (currentName != null) {
                String fragmentContent = content.substring(lastEnd, matcher.start()).trim();
                fragments.add(new ContextFragment(currentName, fragmentContent, currentPriority, currentMandatory));
            }

            currentName = matcher.group(1);
            String priorityStr = matcher.group(2);
            currentPriority = (priorityStr != null) ? Integer.parseInt(priorityStr) : 5;
            currentMandatory = matcher.group(3) != null;
            lastEnd = matcher.end();
        }

        if (currentName != null) {
            String fragmentContent = content.substring(lastEnd).trim();
            fragments.add(new ContextFragment(currentName, fragmentContent, currentPriority, currentMandatory));
        }

        return fragments;
    }
}