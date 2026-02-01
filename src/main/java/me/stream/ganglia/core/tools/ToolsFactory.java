package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final FileSystemTools fileSystemTools;

    public ToolsFactory(Vertx vertx) {
        this.vertx = vertx;
        this.fileSystemTools = new FileSystemTools(vertx);
    }

    public FileSystemTools getFileSystemTools() {
        return fileSystemTools;
    }
    
    // Future expansion: getHttpTools(), getTaskTools(), etc.
}
