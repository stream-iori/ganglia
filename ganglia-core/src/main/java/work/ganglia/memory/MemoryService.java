package work.ganglia.memory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;

/**
 * Background service that handles memory-related tasks like reflection and daily recording.
 * Listens for events on the EventBus to stay decoupled from the main agent loop.
 */
public class MemoryService {
    private static final Logger logger = LoggerFactory.getLogger(MemoryService.class);
    public static final String ADDRESS_REFLECT = "ganglia.memory.reflect";

    private final Vertx vertx;
    private final ContextCompressor compressor;
    private final DailyRecordManager dailyRecordManager;

    public MemoryService(Vertx vertx, ContextCompressor compressor, DailyRecordManager dailyRecordManager) {
        this.vertx = vertx;
        this.compressor = compressor;
        this.dailyRecordManager = dailyRecordManager;
        register();
    }

    private void register() {
        vertx.eventBus().<JsonObject>consumer(ADDRESS_REFLECT, message -> {
            try {
                ReflectEvent event = message.body().mapTo(ReflectEvent.class);
                handleReflect(event);
            } catch (Exception e) {
                logger.error("Failed to parse ReflectEvent from message: {}", message.body(), e);
            }
        });
        logger.info("MemoryService registered on address: {}", ADDRESS_REFLECT);
    }

    private void handleReflect(ReflectEvent event) {
        logger.debug("Starting background reflection for session: {}", event.sessionId());

        compressor.reflect(event.turn())
            .compose(summary -> dailyRecordManager.record(event.sessionId(), event.goal(), summary))
            .onSuccess(v -> logger.debug("Background reflection and recording completed for session: {}", event.sessionId()))
            .onFailure(err -> {
                if (isShutdownError(err)) {
                    logger.debug("Background task for session {} was aborted due to shutdown.", event.sessionId());
                } else {
                    logger.error("Background reflection failed for session: {}", event.sessionId(), err);
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
