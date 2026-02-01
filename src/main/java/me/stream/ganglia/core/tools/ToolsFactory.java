package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final VertxFileSystemTools vertxFileSystemTools;
    private final BashFileSystemTools bashFileSystemTools;

    public ToolsFactory(Vertx vertx) {
        this.vertx = vertx;
        this.vertxFileSystemTools = new VertxFileSystemTools(vertx);
        this.bashFileSystemTools = new BashFileSystemTools(vertx);
    }

    public VertxFileSystemTools getVertxFileSystemTools() {
        return vertxFileSystemTools;
    }

    public BashFileSystemTools getBashFileSystemTools() {
        return bashFileSystemTools;
    }
}
