package work.ganglia.coding.tool;

import io.vertx.core.Vertx;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.infrastructure.internal.prompt.context.FileContextSource;
import work.ganglia.infrastructure.internal.prompt.context.MarkdownContextResolver;
import work.ganglia.util.PathSanitizer;
import work.ganglia.util.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating and managing coding-specific tool sets and context sources.
 */
public class CodingToolsFactory {
    private final Vertx vertx;
    private final String projectRoot;
    private final PathSanitizer sanitizer;

    public CodingToolsFactory(Vertx vertx, String projectRoot) {
        this.vertx = vertx;
        this.projectRoot = projectRoot;
        this.sanitizer = new PathSanitizer(projectRoot);
    }

    public List<ToolSet> createToolSets() {
        List<ToolSet> toolSets = new ArrayList<>();
        toolSets.add(new BashFileSystemTools(vertx, sanitizer));
        toolSets.add(new BashTools(vertx));
        toolSets.add(new FileEditTools(vertx, sanitizer));
        toolSets.add(new WebFetchTools(vertx));
        return toolSets;
    }

    public List<ContextSource> createContextSources() {
        List<ContextSource> sources = new ArrayList<>();
        MarkdownContextResolver resolver = new MarkdownContextResolver(vertx);
        sources.add(new FileContextSource(vertx, resolver, Constants.FILE_GANGLIA_MD));
        return sources;
    }
}
