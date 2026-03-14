package work.ganglia.infrastructure.external.tool;

import work.ganglia.kernel.todo.ToDoTools;
import io.vertx.core.Vertx;
import work.ganglia.util.PathSanitizer;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.port.internal.memory.LongTermMemory;

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

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, LongTermMemory longTermMemory) {
        this(vertx, compressor, longTermMemory, System.getProperty("user.dir"));
    }

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, LongTermMemory longTermMemory, String projectRoot) {
        this.vertx = vertx;
        PathSanitizer sanitizer = new PathSanitizer(projectRoot);
        this.bashFileSystemTools = new BashFileSystemTools(vertx, sanitizer);
        this.toDoTools = new ToDoTools(vertx, compressor);
        this.knowledgeBaseTools = new KnowledgeBaseTools(vertx, longTermMemory);
        this.interactionTools = new InteractionTools(vertx);
        this.webFetchTools = new WebFetchTools(vertx);
        this.bashTools = new BashTools(vertx);
        this.fileEditTools = new FileEditTools(vertx, sanitizer);
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
}
