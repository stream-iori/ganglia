package me.stream.ganglia.memory;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.Turn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            JsonObject body = message.body();
            String sessionId = body.getString("sessionId");
            String goal = body.getString("goal");
            JsonObject turnJson = body.getJsonObject("turn");

            if (sessionId == null || goal == null || turnJson == null) {
                logger.error("Invalid reflect event received: {}", body);
                return;
            }

            try {
                Turn turn = turnJson.mapTo(Turn.class);
                handleReflect(sessionId, goal, turn);
            } catch (Exception e) {
                logger.error("Failed to parse Turn object from event for session: {}", sessionId, e);
            }
        });
        logger.info("MemoryService registered on address: {}", ADDRESS_REFLECT);
    }

    private void handleReflect(String sessionId, String goal, Turn turn) {
        logger.debug("Starting background reflection for session: {}", sessionId);
        
        compressor.reflect(turn)
            .compose(summary -> dailyRecordManager.record(sessionId, goal, summary))
            .onSuccess(v -> logger.debug("Background reflection and recording completed for session: {}", sessionId))
            .onFailure(err -> {
                if (isShutdownError(err)) {
                    logger.debug("Background task for session {} was aborted due to shutdown.", sessionId);
                } else {
                    logger.error("Background reflection failed for session: {}", sessionId, err);
                }
            });
    }

    private boolean isShutdownError(Throwable err) {
        if (err == null) return false;
        if (err instanceof java.util.concurrent.RejectedExecutionException) return true;
        if (err instanceof java.util.concurrent.CompletionException && err.getCause() instanceof java.util.concurrent.RejectedExecutionException) return true;
        if (err.getMessage() != null && err.getMessage().contains("rejected from java.util.concurrent.ThreadPoolExecutor")) return true;
        return isShutdownError(err.getCause());
    }
}
