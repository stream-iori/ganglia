package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.KnowledgeBase;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final VertxFileSystemTools vertxFileSystemTools;
    private final BashFileSystemTools bashFileSystemTools;
    private final ToDoTools toDoTools;
    private final KnowledgeBaseTools knowledgeBaseTools;
    private final InteractionTools interactionTools;
    private final WebFetchTools webFetchTools;
    private final BashTools bashTools;

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, KnowledgeBase knowledgeBase) {
        this.vertx = vertx;
        this.vertxFileSystemTools = new VertxFileSystemTools(vertx);
        this.bashFileSystemTools = new BashFileSystemTools(vertx);
        this.toDoTools = new ToDoTools(vertx, compressor);
        this.knowledgeBaseTools = new KnowledgeBaseTools(vertx, knowledgeBase);
        this.interactionTools = new InteractionTools(vertx);
        this.webFetchTools = new WebFetchTools(vertx);
        this.bashTools = new BashTools(vertx);
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

    public InteractionTools getInteractionTools() {
        return interactionTools;
    }

    public WebFetchTools getWebFetchTools() {
        return webFetchTools;
    }

    public BashTools getBashTools() {
        return bashTools;
    }
}
