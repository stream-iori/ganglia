package work.ganglia.trading.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.trading.web.model.JsonRpcRequest;

class TradingRpcHandlerTest {

  private TradingRpcHandler handler;
  private TradingSessionManager sessions;
  private TradingWebSocketHandler wsHandler;
  private ServerWebSocket ws;

  @BeforeEach
  void setUp() {
    sessions = new TradingSessionManager();
    wsHandler = new TradingWebSocketHandler(sessions);
    TradingPipelineOrchestrator orchestrator = mock(TradingPipelineOrchestrator.class);
    InMemoryBlackboard blackboard = new InMemoryBlackboard();
    handler =
        new TradingRpcHandler(
            orchestrator, blackboard, sessions, wsHandler, TradingConfig.defaults());
    wsHandler.setRpcHandler(handler);

    ws = mock(ServerWebSocket.class);
    when(ws.isClosed()).thenReturn(false);
    when(ws.writeTextMessage(anyString())).thenReturn(Future.succeededFuture());
  }

  @Test
  void configRoundTrip() {
    assertEquals("VALUE", handler.getConfig().investmentStyle().name());

    TradingConfig newConfig =
        new JsonObject()
            .put("investmentStyle", "GROWTH")
            .put("maxDebateRounds", 5)
            .put("maxRiskDiscussRounds", 3)
            .put("outputLanguage", "zh")
            .put("instrumentContext", "crypto")
            .put("dataVendor", "YFINANCE")
            .put("fallbackVendor", "ALPHA_VANTAGE")
            .put("enableMemoryTwr", false)
            .put("memoryHalfLifeDays", 90)
            .mapTo(TradingConfig.class);

    handler.updateConfig(newConfig);
    assertEquals("GROWTH", handler.getConfig().investmentStyle().name());
  }

  @Test
  void handleNullSessionIdIsNoOp() {
    JsonRpcRequest request =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "GET_CONFIG")
            .mapTo(JsonRpcRequest.class);

    handler.handle(request, ws, null);
    verify(ws, never()).writeTextMessage(anyString());
  }

  @Test
  void handleSyncSendsResponseWithHistory() {
    sessions.addSocket("s1", ws);
    sessions.cacheEvent("s1", new JsonObject().put("cached", true));

    JsonRpcRequest request =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "SYNC")
            .put("params", new JsonObject().put("sessionId", "s1"))
            .mapTo(JsonRpcRequest.class);

    handler.handle(request, ws, "s1");

    // Should have sent at least one response (the RPC response with history)
    verify(ws, atLeastOnce()).writeTextMessage(anyString());
  }

  @Test
  void busySessionReturnsBusyStatus() {
    sessions.addSocket("s1", ws);
    sessions.markActive("s1");

    JsonRpcRequest request =
        new JsonObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("method", "RUN_PIPELINE")
            .put("params", new JsonObject().put("sessionId", "s1").put("ticker", "AAPL"))
            .mapTo(JsonRpcRequest.class);

    handler.handle(request, ws, "s1");

    verify(ws, atLeastOnce()).writeTextMessage(argThat(msg -> msg.contains("\"busy\"")));
  }
}
