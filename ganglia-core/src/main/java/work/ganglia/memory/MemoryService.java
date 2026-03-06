package work.ganglia.memory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

/**
 * Background service that acts as a registry and event dispatcher for memory modules.
 * Listens for memory events on the EventBus and delegates to registered modules.
 */
public class MemoryService {
    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    public static final String ADDRESS_EVENT = "ganglia.memory.event";

    private final Vertx vertx;
    private final List<MemoryModule> modules = new ArrayList<>();

    public MemoryService(Vertx vertx) {
        this.vertx = vertx;
        register();
    }

    public void registerModule(MemoryModule module) {
        modules.add(module);
        logger.debug("Registered MemoryModule: {}", module.id());
    }

    public List<MemoryModule> getModules() {
        return new ArrayList<>(modules);
    }

    private void register() {
        vertx.eventBus().<JsonObject>consumer(ADDRESS_EVENT, message -> {
            try {
                MemoryEvent event = message.body().mapTo(MemoryEvent.class);
                handleEvent(event);
            } catch (Exception e) {
                // For backward compatibility during migration, try ReflectEvent
                try {
                    ReflectEvent oldEvent = message.body().mapTo(ReflectEvent.class);
                    MemoryEvent event = new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, oldEvent.sessionId(), oldEvent.goal(), oldEvent.turn());
                    handleEvent(event);
                } catch (Exception ex) {
                    logger.error("Failed to parse MemoryEvent from message: {}", message.body(), e);
                }
            }
        });
        logger.info("MemoryService registered on address: {}", ADDRESS_EVENT);
    }

    private void handleEvent(MemoryEvent event) {
        logger.debug("Dispatching memory event {} for session: {}", event.type(), event.sessionId());

        List<Future<Void>> futures = modules.stream()
                .map(module -> module.onEvent(event).recover(err -> {
                    if (!isShutdownError(err)) {
                        logger.error("Module {} failed to handle event {}", module.id(), event.type(), err);
                    }
                    return Future.succeededFuture(); // Don't fail the whole batch
                }))
                .toList();

        Future.all(futures).onComplete(ar -> {
            if (ar.succeeded()) {
                logger.debug("Memory event {} completed for session: {}", event.type(), event.sessionId());
            } else {
                logger.error("Memory event dispatch failed for session: {}", event.sessionId(), ar.cause());
            }
        });
    }

    private boolean isShutdownError(Throwable err) {
        if (err == null) return false;
        if (err instanceof RejectedExecutionException) return true;
        if (err instanceof CompletionException && err.getCause() instanceof RejectedExecutionException) return true;
        if (err.getMessage() != null && err.getMessage().contains("rejected from java.util.concurrent.ThreadPoolExecutor")) return true;
        return isShutdownError(err.getCause());
    }
}