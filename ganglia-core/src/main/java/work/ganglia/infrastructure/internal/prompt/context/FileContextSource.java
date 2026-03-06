package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextSource;

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
                                .compose(buffer -> resolver.parse(filePath, buffer.toString()));
                    }
                    return Future.succeededFuture(Collections.emptyList());
                });
    }
}
