package work.ganglia.trading.web;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.trading.web.model.TradingEventType;
import work.ganglia.trading.web.model.TradingServerEvent;
import work.ganglia.trajectory.event.BaseEventType;
import work.ganglia.trajectory.event.CommonEventData;
import work.ganglia.trajectory.publish.AbstractEventPublisher;

/**
 * Bridges ObservationDispatcher events to trading-specific WebSocket events via Vert.x EventBus.
 * Uses a handler registry instead of a monolithic switch statement (OCP compliant).
 */
public class TradingEventPublisher extends AbstractEventPublisher {
  private static final Logger logger = LoggerFactory.getLogger(TradingEventPublisher.class);

  static final String ADDRESS_EVENTS = "trading.ui.ws.events";
  static final String ADDRESS_CACHE = "trading.ui.ws.cache";

  @FunctionalInterface
  interface ObservationHandler {
    void handle(String sessionId, String content, Map<String, Object> data);
  }

  private final Map<ObservationType, ObservationHandler> handlers;

  public TradingEventPublisher(Vertx vertx) {
    super(vertx);
    this.handlers = buildHandlers();
  }

  @Override
  protected String eventsAddress() {
    return ADDRESS_EVENTS;
  }

  @Override
  protected String cacheAddress() {
    return ADDRESS_CACHE;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void onObservation(
      String sessionId,
      ObservationType type,
      String content,
      Map<String, Object> data,
      String spanId,
      String parentSpanId) {
    ObservationHandler handler = handlers.get(type);
    if (handler != null) {
      handler.handle(sessionId, content, data);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<ObservationType, ObservationHandler> buildHandlers() {
    Map<ObservationType, ObservationHandler> map = new EnumMap<>(ObservationType.class);

    map.put(
        ObservationType.MANAGER_CYCLE_STARTED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.DEBATE_CYCLE_STARTED,
                new TradingServerEvent.DebateCycleData(
                    extractString(data, "debateType", "RESEARCH"),
                    extractInt(data, "cycleNumber", 0),
                    extractInt(data, "maxCycles", 0),
                    null)));

    map.put(
        ObservationType.MANAGER_CYCLE_FINISHED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.DEBATE_CYCLE_FINISHED,
                new TradingServerEvent.DebateCycleData(
                    extractString(data, "debateType", "RESEARCH"),
                    extractInt(data, "cycleNumber", 0),
                    extractInt(data, "maxCycles", 0),
                    extractString(data, "decisionType", "CONTINUE"))));

    map.put(
        ObservationType.MANAGER_GRAPH_CONVERGED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.DEBATE_CONVERGED,
                Map.of("totalCycles", extractInt(data, "totalCycles", 0))));

    map.put(
        ObservationType.MANAGER_GRAPH_STALLED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.DEBATE_STALLED,
                Map.of("reason", content != null ? content : "No progress detected")));

    map.put(
        ObservationType.FACT_PUBLISHED,
        (sessionId, content, data) -> {
          Map<String, String> tags =
              data != null && data.containsKey("tags")
                  ? (Map<String, String>) data.get("tags")
                  : Map.of();
          publish(
              sessionId,
              TradingEventType.FACT_PUBLISHED,
              new TradingServerEvent.FactEventData(
                  extractString(data, "factId", ""),
                  content != null ? content : "",
                  extractString(data, "sourceManager", ""),
                  extractInt(data, "cycleNumber", 0),
                  tags));
        });

    map.put(
        ObservationType.FACT_SUPERSEDED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.FACT_SUPERSEDED,
                new TradingServerEvent.FactSupersededData(
                    extractString(data, "factId", ""), extractString(data, "reason", ""))));

    map.put(
        ObservationType.FACT_ARCHIVED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.FACT_ARCHIVED,
                Map.of("factId", extractString(data, "factId", ""))));

    map.put(
        ObservationType.TOKEN_RECEIVED,
        (sessionId, content, data) -> {
          if (content != null && !content.isEmpty()) {
            publish(sessionId, BaseEventType.TOKEN, new CommonEventData.TokenData(content), false);
          }
        });

    map.put(
        ObservationType.REASONING_FINISHED,
        (sessionId, content, data) -> {
          if (content != null && !content.isBlank()) {
            publish(sessionId, BaseEventType.THOUGHT, new CommonEventData.ThoughtData(content));
          }
        });

    map.put(
        ObservationType.TURN_FINISHED,
        (sessionId, content, data) -> {
          if (content != null && !content.isBlank()) {
            publish(
                sessionId,
                BaseEventType.AGENT_MESSAGE,
                new CommonEventData.AgentMessageData(content));
          }
        });

    map.put(
        ObservationType.TOOL_STARTED,
        (sessionId, content, data) ->
            publish(
                sessionId,
                BaseEventType.TOOL_START,
                new CommonEventData.ToolStartData(
                    extractString(data, "toolCallId", UUID.randomUUID().toString()),
                    content != null ? content : "",
                    extractString(data, "command", content))));

    map.put(
        ObservationType.TOOL_FINISHED,
        (sessionId, content, data) -> {
          boolean isError = extractBoolean(data, "isError", false);
          publish(
              sessionId,
              BaseEventType.TOOL_RESULT,
              new CommonEventData.ToolResultData(
                  extractString(data, "toolCallId", ""),
                  extractInt(data, "exitCode", 0),
                  extractString(data, "summary", "Executed: " + content),
                  extractString(data, "fullOutput", content),
                  isError));
        });

    map.put(
        ObservationType.ERROR,
        (sessionId, content, data) ->
            publish(
                sessionId,
                TradingEventType.PIPELINE_ERROR,
                Map.of(
                    "code",
                    extractString(data, "errorCode", "PIPELINE_ERROR"),
                    "message",
                    content != null ? content : "")));

    return map;
  }
}
