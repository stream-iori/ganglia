package work.ganglia.kernel.loop;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import work.ganglia.port.external.tool.ObservationEvent;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.util.Constants;

/**
 * Default implementation of ObservationDispatcher that publishes events to the Vert.x EventBus. It
 * replaces the old EventBusObservationPublisher.
 */
public class DefaultObservationDispatcher implements ObservationDispatcher, AgentLoopObserver {
  private final Vertx vertx;
  private final java.util.List<AgentLoopObserver> localObservers =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  public DefaultObservationDispatcher(Vertx vertx) {
    this.vertx = vertx;
  }

  /** Registers a local observer to receive observations directly. */
  public void register(AgentLoopObserver observer) {
    if (observer != null) {
      localObservers.add(observer);
    }
  }

  @Override
  public void dispatch(String sessionId, ObservationType type, String content) {
    dispatch(sessionId, type, content, null);
  }

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(DefaultObservationDispatcher.class);

  @Override
  public void dispatch(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    if (type == ObservationType.REASONING_STARTED) {
      logger.info("[SESSION:{}] Agent triggered reasoning loop.", sessionId);
    }

    // 1. Notify local observers directly (Synchronous/Reliable)
    for (AgentLoopObserver observer : localObservers) {
      try {
        observer.onObservation(sessionId, type, content, data);
      } catch (Exception e) {
        // Ignore observer errors
      }
    }

    // 2. Publish to EventBus (Asynchronous/Decoupled)
    ObservationEvent event = ObservationEvent.of(sessionId, type, content, data);
    JsonObject json = JsonObject.mapFrom(event);
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_PREFIX + sessionId, json);
    vertx.eventBus().publish(Constants.ADDRESS_OBSERVATIONS_ALL, json);
  }

  // --- AgentLoopObserver implementation ---

  @Override
  public void onObservation(
      String sessionId, ObservationType type, String content, Map<String, Object> data) {
    dispatch(sessionId, type, content, data);
  }

  @Override
  public void onUsageRecorded(String sessionId, TokenUsage usage) {
    if (usage != null) {
      // Notify local observers
      for (AgentLoopObserver observer : localObservers) {
        try {
          observer.onUsageRecorded(sessionId, usage);
        } catch (Exception e) {
          // Ignore
        }
      }

      vertx
          .eventBus()
          .publish(
              Constants.ADDRESS_USAGE_RECORD,
              new JsonObject().put("sessionId", sessionId).put("usage", JsonObject.mapFrom(usage)));
    }
  }
}
