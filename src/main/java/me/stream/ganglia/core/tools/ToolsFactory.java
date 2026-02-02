package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.memory.ContextCompressor;
import me.stream.ganglia.core.memory.KnowledgeBase;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final VertxFileSystemTools vertxFileSystemTools;
    private final BashFileSystemTools bashFileSystemTools;
    private final ToDoTools toDoTools;
    private final KnowledgeBaseTools knowledgeBaseTools;

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, KnowledgeBase knowledgeBase) {
        this.vertx = vertx;
        this.vertxFileSystemTools = new VertxFileSystemTools(vertx);
        this.bashFileSystemTools = new BashFileSystemTools(vertx);
        this.toDoTools = new ToDoTools(vertx, compressor);
        this.knowledgeBaseTools = new KnowledgeBaseTools(vertx, knowledgeBase);
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

    public KnowledgeBaseTools getKnowledgeBaseTools() {
        return knowledgeBaseTools;
    }
}
