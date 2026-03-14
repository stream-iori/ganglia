package work.ganglia.infrastructure.internal.memory;

import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryModule;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.memory.MemoryEvent;

import java.util.List;

/**
 * Memory module representing the long-term project knowledge base.
 */
public class LongTermKnowledgeModule implements MemoryModule {
    private final LongTermMemory longTermMemory;

    public LongTermKnowledgeModule(LongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    @Override
    public String id() {
        return "long-term-knowledge";
    }

    @Override
    public Future<List<ContextFragment>> provideContext(SessionContext context) {
        String content = """
                - You have access to a persistent knowledge base (MEMORY.md).
                - Use the 'remember' tool to save important facts, user preferences, or architectural decisions.
                - Use 'grep' or 'read' to search MEMORY.md if you need to recall project context.
                """;
        return Future.succeededFuture(List.of(new ContextFragment("Memory & Context", content, ContextFragment.PRIORITY_MEMORY, false)));
    }

    @Override
    public Future<Void> onEvent(MemoryEvent event) {
        return Future.succeededFuture();
    }
}