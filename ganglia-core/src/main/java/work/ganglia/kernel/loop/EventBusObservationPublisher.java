package work.ganglia.kernel.loop;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.util.Constants;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;

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
        vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_PREFIX + sessionId, JsonObject.mapFrom(event));
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