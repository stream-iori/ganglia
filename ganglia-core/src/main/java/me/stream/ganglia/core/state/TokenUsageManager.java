package me.stream.ganglia.core.state;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks and persists token usage for agent sessions.
 */
public class TokenUsageManager {
    private static final Logger logger = LoggerFactory.getLogger(TokenUsageManager.class);
    public static final String ADDRESS_RECORD = "ganglia.usage.record";

    private final Vertx vertx;
    private final Map<String, SessionUsage> sessionTotals = new ConcurrentHashMap<>();

    public TokenUsageManager(Vertx vertx) {
        this.vertx = vertx;
        register();
    }

    private void register() {
        vertx.eventBus().<JsonObject>consumer(ADDRESS_RECORD, message -> {
            JsonObject body = message.body();
            String sessionId = body.getString("sessionId");
            JsonObject usageJson = body.getJsonObject("usage");

            if (sessionId == null || usageJson == null) {
                logger.error("Invalid usage event received: {}", body);
                return;
            }

            try {
                TokenUsage usage = usageJson.mapTo(TokenUsage.class);
                recordUsage(sessionId, usage);
            } catch (Exception e) {
                logger.error("Failed to parse TokenUsage object from event for session: {}", sessionId, e);
            }
        });
        logger.info("TokenUsageManager registered on address: {}", ADDRESS_RECORD);
    }

    private void recordUsage(String sessionId, TokenUsage usage) {
        SessionUsage totals = sessionTotals.computeIfAbsent(sessionId, id -> new SessionUsage());
        totals.add(usage);
        
        logger.debug("Recorded usage for session: {}. Prompt: {}, Completion: {}. [Total: P={}, C={}]", 
            sessionId, usage.promptTokens(), usage.completionTokens(), totals.promptTokens(), totals.completionTokens());
        
        // Emitting observation event for visibility (optional)
        publishUsageUpdate(sessionId, usage, totals);
    }

    private void publishUsageUpdate(String sessionId, TokenUsage lastUsage, SessionUsage totalUsage) {
        // Here we can publish an event or log it in a more structured way
        // Currently, we just log it as debug.
    }

    public SessionUsage getTotals(String sessionId) {
        return sessionTotals.get(SessionUsage.EMPTY_USAGE);
    }

    public static class SessionUsage {
        private static final String EMPTY_USAGE = "empty";
        private final AtomicInteger promptTokens = new AtomicInteger(0);
        private final AtomicInteger completionTokens = new AtomicInteger(0);

        public void add(TokenUsage usage) {
            promptTokens.addAndGet(usage.promptTokens());
            completionTokens.addAndGet(usage.completionTokens());
        }

        public int promptTokens() {
            return promptTokens.get();
        }

        public int completionTokens() {
            return completionTokens.get();
        }
    }
}
