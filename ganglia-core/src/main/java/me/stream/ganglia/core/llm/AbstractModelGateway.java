package me.stream.ganglia.core.llm;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.Message;
import me.stream.ganglia.core.model.ObservationEvent;
import me.stream.ganglia.core.model.ObservationType;
import me.stream.ganglia.core.model.Role;

import java.util.concurrent.Semaphore;

/**
 * Base class for ModelGateways to reduce boilerplate and enforce common constraints.
 */
public abstract class AbstractModelGateway implements ModelGateway {
    protected final Vertx vertx;
    private final Semaphore semaphore = new Semaphore(5); // Limit to 5 concurrent calls

    protected AbstractModelGateway(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Publishes a token received event to the EventBus.
     */
    protected void publishToken(String sessionId, String token) {
        if (token == null || token.isEmpty()) return;
        String address = "ganglia.observations." + sessionId;
        ObservationEvent obs = ObservationEvent.of(sessionId, ObservationType.TOKEN_RECEIVED, token);
        vertx.eventBus().publish(address, JsonObject.mapFrom(obs));
    }

    /**
     * Common logic to merge multiple system messages into one if needed.
     */
    protected String mergeSystemMessages(java.util.List<Message> history) {
        return history.stream()
            .filter(m -> m.role() == Role.SYSTEM)
            .map(Message::content)
            .reduce((a, b) -> a + "\n" + b)
            .orElse(null);
    }

    /**
     * Wraps a future with semaphore protection to limit concurrency.
     */
    protected <T> Future<T> withSemaphore(Future<T> future) {
        if (semaphore.tryAcquire()) {
            return future.onComplete(v -> semaphore.release());
        } else {
            return Future.failedFuture(new LLMException("Concurrency limit reached (max 5)", null, 429, null, null));
        }
    }
}
