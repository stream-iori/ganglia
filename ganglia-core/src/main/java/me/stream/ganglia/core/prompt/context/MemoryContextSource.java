package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;

import java.util.List;

public class MemoryContextSource implements ContextSource {
    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        String content = """
                - You have access to a persistent knowledge base (MEMORY.md).
                - Use the 'remember' tool to save important facts, user preferences, or architectural decisions.
                - Use 'grep' or 'read' to search MEMORY.md if you need to recall project context.
                """;
        return Future.succeededFuture(List.of(new ContextFragment("Memory & Context", content, ContextFragment.PRIORITY_MEMORY, false)));
    }
}
