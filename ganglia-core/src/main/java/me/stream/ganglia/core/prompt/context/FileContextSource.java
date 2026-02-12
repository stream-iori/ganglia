package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.ganglia.core.model.SessionContext;

import java.util.Collections;
import java.util.List;

/**
 * Loads context fragments from a specific file.
 */
public class FileContextSource implements ContextSource {
    private final Vertx vertx;
    private final MarkdownContextResolver resolver;
    private final String filePath;

    public FileContextSource(Vertx vertx, MarkdownContextResolver resolver, String filePath) {
        this.vertx = vertx;
        this.resolver = resolver;
        this.filePath = filePath;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        return vertx.fileSystem().exists(filePath)
                .compose(exists -> {
                    if (exists) {
                        return vertx.fileSystem().readFile(filePath)
                                .map(buffer -> resolver.parse(filePath, buffer.toString()));
                    }
                    return Future.succeededFuture(Collections.emptyList());
                });
    }
}
