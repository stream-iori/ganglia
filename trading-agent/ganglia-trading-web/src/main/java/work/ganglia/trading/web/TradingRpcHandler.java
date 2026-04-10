package work.ganglia.trading.web;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.trading.signal.SignalExtractor.TradingSignal;
import work.ganglia.trading.web.model.JsonRpcRequest;
import work.ganglia.trading.web.model.JsonRpcResponse;
import work.ganglia.trading.web.model.TradingEventType;
import work.ganglia.trading.web.model.TradingServerEvent;

/**
 * Handles JSON-RPC method dispatch for WebSocket messages. Extracted from TradingWebVerticle for
 * single responsibility.
 */
class TradingRpcHandler {
  private static final Logger logger = LoggerFactory.getLogger(TradingRpcHandler.class);

  private final TradingPipelineOrchestrator orchestrator;
  private final Blackboard blackboard;
  private final TradingSessionManager sessions;
  private final TradingWebSocketHandler wsHandler;
  private volatile TradingConfig tradingConfig;

  TradingRpcHandler(
      TradingPipelineOrchestrator orchestrator,
      Blackboard blackboard,
      TradingSessionManager sessions,
      TradingWebSocketHandler wsHandler,
      TradingConfig tradingConfig) {
    this.orchestrator = orchestrator;
    this.blackboard = blackboard;
    this.sessions = sessions;
    this.wsHandler = wsHandler;
    this.tradingConfig = tradingConfig;
  }

  void updateConfig(TradingConfig config) {
    this.tradingConfig = config;
  }

  TradingConfig getConfig() {
    return tradingConfig;
  }

  void handle(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    if (sessionId == null) return;

    switch (request.method()) {
      case "SYNC" -> handleSync(request, ws, sessionId);
      case "RUN_PIPELINE" -> handleRunPipeline(request, ws, sessionId);
      case "GET_CONFIG" -> handleGetConfig(request, ws, sessionId);
      case "UPDATE_CONFIG" -> handleUpdateConfig(request, ws, sessionId);
      case "GET_MEMORY" -> handleGetMemory(request, ws, sessionId);
      default -> logger.warn("Unknown RPC method: {}", request.method());
    }
  }

  private void handleSync(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    wsHandler.publishEvent(
        sessionId,
        TradingEventType.INIT_CONFIG,
        new TradingServerEvent.TradingInitConfigData(null, tradingConfig));

    List<JsonObject> history = sessions.getHistory(sessionId);
    sendResponse(ws, request.id(), new JsonObject().put("history", new JsonArray(history)));
  }

  private void handleRunPipeline(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    if (sessions.isSessionActive(sessionId)) {
      sendResponse(ws, request.id(), new JsonObject().put("status", "busy"));
      return;
    }

    String ticker = request.params().getString("ticker", "AAPL");
    sessions.markActive(sessionId);

    wsHandler.publishEvent(
        sessionId,
        TradingEventType.PIPELINE_STARTED,
        new TradingServerEvent.PipelinePhaseData("PERCEPTION", "RUNNING", ticker));

    SessionContext pipelineContext =
        new SessionContext(sessionId, null, null, null, null, null, null);

    orchestrator
        .execute(ticker, pipelineContext)
        .onSuccess(result -> onPipelineSuccess(sessionId, ticker, result))
        .onFailure(err -> onPipelineFailure(sessionId, err));

    sendResponse(ws, request.id(), new JsonObject().put("status", "started"));
  }

  private void onPipelineSuccess(
      String sessionId, String ticker, TradingPipelineOrchestrator.PipelineResult result) {
    TradingSignal signal = result.signal();
    sessions.addSignal(
        new TradingServerEvent.SignalHistoryEntry(
            ticker,
            signal.signal().name(),
            signal.confidence(),
            signal.rationale(),
            System.currentTimeMillis()));

    wsHandler.publishEvent(
        sessionId,
        TradingEventType.PIPELINE_COMPLETED,
        new TradingServerEvent.PipelineCompletedData(
            signal.signal().name(),
            signal.confidence(),
            signal.rationale(),
            result.perceptionReport(),
            result.debateReport(),
            result.riskReport()));

    sessions.markInactive(sessionId);
  }

  private void onPipelineFailure(String sessionId, Throwable err) {
    wsHandler.publishEvent(
        sessionId,
        TradingEventType.PIPELINE_ERROR,
        Map.of("code", "PIPELINE_FAILURE", "message", err.getMessage()));
    sessions.markInactive(sessionId);
  }

  private void handleGetConfig(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    wsHandler.publishEvent(
        sessionId,
        TradingEventType.INIT_CONFIG,
        new TradingServerEvent.TradingInitConfigData(null, tradingConfig));
    sendResponse(ws, request.id(), new JsonObject().put("status", "ok"));
  }

  private void handleUpdateConfig(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    try {
      JsonObject configJson = request.params().getJsonObject("config");
      if (configJson != null) {
        this.tradingConfig = configJson.mapTo(TradingConfig.class);
      }
      wsHandler.publishEvent(
          sessionId,
          TradingEventType.INIT_CONFIG,
          new TradingServerEvent.TradingInitConfigData(null, tradingConfig));
      sendResponse(ws, request.id(), new JsonObject().put("status", "ok"));
    } catch (Exception e) {
      sendResponse(
          ws, request.id(), new JsonObject().put("status", "error").put("message", e.getMessage()));
    }
  }

  private void handleGetMemory(JsonRpcRequest request, ServerWebSocket ws, String sessionId) {
    String role = request.params().getString("role");
    Future<? extends List<?>> factsFuture;
    if (role != null && !role.isEmpty()) {
      factsFuture = blackboard.getActiveFacts(Map.of("role", role));
    } else {
      factsFuture = blackboard.getActiveFacts();
    }

    factsFuture.onSuccess(
        facts -> {
          JsonArray arr = new JsonArray();
          facts.forEach(f -> arr.add(JsonObject.mapFrom(f)));
          sendResponse(ws, request.id(), new JsonObject().put("facts", arr));
        });
  }

  private void sendResponse(ServerWebSocket ws, Object id, JsonObject result) {
    if (id != null) {
      ws.writeTextMessage(JsonObject.mapFrom(JsonRpcResponse.success(id, result)).encode());
    }
  }
}
