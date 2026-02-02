package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.memory.ContextCompressor;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final VertxFileSystemTools vertxFileSystemTools;
    private final BashFileSystemTools bashFileSystemTools;
    private final ToDoTools toDoTools;

    public ToolsFactory(Vertx vertx, ContextCompressor compressor) {
        this.vertx = vertx;
        this.vertxFileSystemTools = new VertxFileSystemTools(vertx);
        this.bashFileSystemTools = new BashFileSystemTools(vertx);
        this.toDoTools = new ToDoTools(vertx, compressor);
    }

    public VertxFileSystemTools getVertxFileSystemTools() {
        return vertxFileSystemTools;
    }

    public BashFileSystemTools getBashFileSystemTools() {
        return bashFileSystemTools;
    }

    public ToDoTools getToDoTools() {
        return toDoTools;
    }
}
