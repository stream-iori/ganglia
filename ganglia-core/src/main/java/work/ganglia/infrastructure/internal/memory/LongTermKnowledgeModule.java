package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.internal.prompt.context.ContextFragment;
import work.ganglia.port.internal.memory.MemoryEvent;

import java.util.List;

/**
 * Memory module representing the long-term project knowledge base.
 */
public class LongTermKnowledgeModule implements MemoryModule {
    private final KnowledgeBase knowledgeBase;

    public LongTermKnowledgeModule(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
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