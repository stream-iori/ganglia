package me.stream.ganglia.core.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import java.io.File;

/**
 * Manages the long-term knowledge base (MEMORY.md).
 */
public class KnowledgeBase {
    private final Vertx vertx;
    private final String filePath;

    private static final String DEFAULT_TEMPLATE = """
            # Project Memory
            
            ## User Preferences
            
            ## Project Conventions
            
            ## Architecture Decisions
            
            """;

    public KnowledgeBase(Vertx vertx, String filePath) {
        this.vertx = vertx;
        this.filePath = filePath;
    }

    public KnowledgeBase(Vertx vertx) {
        this(vertx, "MEMORY.md");
    }

    public Future<Void> ensureInitialized() {
        return vertx.fileSystem().exists(filePath)
                .compose(exists -> {
                    if (!exists) {
                        return vertx.fileSystem().writeFile(filePath, Buffer.buffer(DEFAULT_TEMPLATE));
                    }
                    return Future.succeededFuture();
                });
    }

    public Future<String> read() {
        return vertx.fileSystem().readFile(filePath)
                .map(Buffer::toString)
                .recover(err -> Future.succeededFuture("")); // Return empty if error
    }

    public Future<Void> append(String content) {
        return ensureInitialized()
                .compose(v -> vertx.fileSystem().open(filePath, new io.vertx.core.file.OpenOptions().setAppend(true)))
                .compose(asyncFile -> asyncFile.write(Buffer.buffer("\n" + content + "\n")).compose(v -> asyncFile.close()));
    }
}
