package work.ganglia.core.loop;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.core.model.ObservationEvent;
import work.ganglia.core.model.ObservationType;

import java.util.Map;

/**
 * Publishes agent loop observations and usage metrics to the Vert.x EventBus.
 */
public class EventBusObservationPublisher implements AgentLoopObserver {
    private final Vertx vertx;

    public EventBusObservationPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void onObservation(String sessionId, ObservationType type, String content, Map<String, Object> data) {
        ObservationEvent event = ObservationEvent.of(sessionId, type, content, data);
        vertx.eventBus().publish("ganglia.observations." + sessionId, JsonObject.mapFrom(event));
    }

    @Override
    public void onUsageRecorded(String sessionId, TokenUsage usage) {
        if (usage != null) {
            vertx.eventBus().publish("ganglia.usage.record", new JsonObject()
                .put("sessionId", sessionId)
                .put("usage", JsonObject.mapFrom(usage)));
        }
    }
}