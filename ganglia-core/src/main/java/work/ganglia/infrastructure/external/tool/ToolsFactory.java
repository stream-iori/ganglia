package work.ganglia.infrastructure.external.tool;

import work.ganglia.kernel.todo.ToDoTools;
import io.vertx.core.Vertx;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.memory.LongTermMemory;

/**
 * Factory for creating and managing built-in core tool sets.
 */
public class ToolsFactory {
    private final Vertx vertx;
    private final ToDoTools toDoTools;
    private final KnowledgeBaseTools knowledgeBaseTools;
    private final InteractionTools interactionTools;

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, LongTermMemory longTermMemory) {
        this(vertx, compressor, longTermMemory, System.getProperty("user.dir"));
    }

    public ToolsFactory(Vertx vertx, ContextCompressor compressor, LongTermMemory longTermMemory, String projectRoot) {
        this.vertx = vertx;
        this.toDoTools = new ToDoTools(vertx, compressor);
        this.knowledgeBaseTools = new KnowledgeBaseTools(vertx, longTermMemory);
        this.interactionTools = new InteractionTools(vertx);
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
}
