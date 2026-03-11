package work.ganglia.kernel.loop;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.util.Constants;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.TokenUsage;

import java.util.Map;

/**
 * Default implementation of ObservationDispatcher that publishes events to the Vert.x EventBus.
 * It replaces the old EventBusObservationPublisher.
 */
public class DefaultObservationDispatcher implements ObservationDispatcher, AgentLoopObserver {
    private final Vertx vertx;

    public DefaultObservationDispatcher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void dispatch(String sessionId, ObservationType type, String content) {
        dispatch(sessionId, type, content, null);
    }

    @Override
    public void dispatch(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        ObservationEvent event = ObservationEvent.of(sessionId, type, content, data);
        JsonObject json = JsonObject.mapFrom(event);
        
        // Publish to session-specific topic (for legacy or specific listeners)
        vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_PREFIX + sessionId, json);
        
        // Publish to global topic (for WebUI, TraceManager, etc.)
        vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, json);
    }

    // --- AgentLoopObserver implementation ---
    // This allows the dispatcher to be registered with the loop to catch macro events
    // and seamlessly route them through the same dispatch logic.

    @Override
    public void onObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        dispatch(sessionId, type, content, data);
    }

    @Override
    public void onUsageRecorded(String sessionId, TokenUsage usage) {
        if (usage != null) {
            vertx.eventBus().publish(Constants.ADDRESS_USAGE_RECORD, new JsonObject()
                .put("sessionId", sessionId)
                .put("usage", JsonObject.mapFrom(usage)));
        }
    }
}
