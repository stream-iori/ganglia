package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.memory.MemoryModule;
import work.ganglia.port.internal.memory.MemoryService;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.ArrayList;
import java.util.List;

public class MemoryContextSource implements ContextSource {
    private final MemoryService memoryService;

    public MemoryContextSource(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        if (memoryService == null) {
            return Future.succeededFuture(List.of());
        }

        List<Future<List<ContextFragment>>> futures = memoryService.getModules().stream()
                .map(module -> module.provideContext(sessionContext).recover(err -> Future.succeededFuture(List.of())))
                .toList();

        return Future.all(futures).map(ar -> {
            List<ContextFragment> allFragments = new ArrayList<>();
            for (int i = 0; i < futures.size(); i++) {
                List<ContextFragment> fragments = ar.resultAt(i);
                if (fragments != null) {
                    allFragments.addAll(fragments);
                }
            }
            return allFragments;
        });
    }
}