package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.prompt.PromptEngine;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.core.util.PathSanitizer;
import me.stream.ganglia.memory.ContextCompressor;
import me.stream.ganglia.memory.KnowledgeBase;
import me.stream.ganglia.tools.subagent.DefaultGraphExecutor;
import me.stream.ganglia.tools.subagent.GraphExecutor;

/**
 * Factory for creating and managing built-in tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final BashFileSystemTools bashFileSystemTools;
    private final ToDoTools toDoTools;
    private final KnowledgeBaseTools knowledgeBaseTools;
    private final InteractionTools interactionTools;
    private final WebFetchTools webFetchTools;
    private final BashTools bashTools;
    private final FileEditTools fileEditTools;

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, KnowledgeBase knowledgeBase) {
        this(vertx, compressor, knowledgeBase, System.getProperty("user.dir"));
    }

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, KnowledgeBase knowledgeBase, String projectRoot) {
        this.vertx = vertx;
        this.bashFileSystemTools = new BashFileSystemTools(vertx, new PathSanitizer(projectRoot));
        this.toDoTools = new ToDoTools(vertx, compressor);
        this.knowledgeBaseTools = new KnowledgeBaseTools(vertx, knowledgeBase);
        this.interactionTools = new InteractionTools(vertx);
        this.webFetchTools = new WebFetchTools(vertx);
        this.bashTools = new BashTools(vertx);
        this.fileEditTools = new FileEditTools(vertx);
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

    public FileEditTools getFileEditTools() {
        return fileEditTools;
    }

    public SubAgentTools createSubAgentTools(ModelGateway model, SessionManager sessionManager, PromptEngine promptEngine, ConfigManager config, ToolExecutor executor, ContextCompressor compressor) {
        GraphExecutor graphExecutor = new DefaultGraphExecutor(vertx, model, executor, sessionManager, promptEngine, config, compressor);
        return new SubAgentTools(vertx, model, sessionManager, promptEngine, config, executor, graphExecutor, compressor);
    }
}
