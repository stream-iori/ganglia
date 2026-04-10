package work.ganglia.trading.web;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.web.model.JsonRpcNotification;
import work.ganglia.trading.web.model.JsonRpcRequest;
import work.ganglia.trading.web.model.TradingEventType;
import work.ganglia.trading.web.model.TradingServerEvent;

/**
 * Manages WebSocket lifecycle: handshake acceptance, message parsing, event forwarding. Extracted
 * from TradingWebVerticle for single responsibility.
 */
class TradingWebSocketHandler {
  private static final Logger logger = LoggerFactory.getLogger(TradingWebSocketHandler.class);

  private final TradingSessionManager sessions;
  private TradingRpcHandler rpcHandler;

  TradingWebSocketHandler(TradingSessionManager sessions) {
    this.sessions = sessions;
  }

  void setRpcHandler(TradingRpcHandler rpcHandler) {
    this.rpcHandler = rpcHandler;
  }

  void handleHandshake(io.vertx.core.http.ServerWebSocketHandshake handshake) {
    String path = handshake.path();
    if ("/ws".equals(path)) {
      handshake.accept().onSuccess(this::setupMainWebSocket);
    } else if ("/ws/traces".equals(path)) {
      handshake.accept().onSuccess(this::setupTraceWebSocket);
    } else {
      handshake.reject();
    }
  }

  private void setupMainWebSocket(ServerWebSocket ws) {
    logger.info("Trading WebSocket connected");
    AtomicReference<String> currentSessionId = new AtomicReference<>();

    ws.textMessageHandler(
        text -> {
          try {
            JsonRpcRequest request = new JsonObject(text).mapTo(JsonRpcRequest.class);
            if (!"2.0".equals(request.jsonrpc()) || request.method() == null) return;

            String sessionId =
                request.params() != null ? request.params().getString("sessionId") : null;
            if (sessionId != null) {
              currentSessionId.set(sessionId);
              sessions.addSocket(sessionId, ws);
            }

            rpcHandler.handle(request, ws, sessionId);
          } catch (Exception e) {
            logger.error("Failed to parse websocket message: {}", text, e);
          }
        });

    ws.closeHandler(
        v -> {
          String lastSessionId = currentSessionId.get();
          if (lastSessionId != null) {
            sessions.removeSocket(lastSessionId, ws);
          }
          logger.info("Trading WebSocket closed");
        });
  }

  private void setupTraceWebSocket(ServerWebSocket ws) {
    logger.info("Trading Trace WebSocket connected");
    sessions.addTraceClient(ws);
    ws.closeHandler(
        v -> {
          sessions.removeTraceClient(ws);
          logger.info("Trading Trace WebSocket closed");
        });
    ws.exceptionHandler(err -> sessions.removeTraceClient(ws));
  }

  // ── Event publishing ────────────────────────────────────────────────

  void publishEvent(String sessionId, TradingEventType type, Object data) {
    TradingServerEvent event =
        new TradingServerEvent(
            UUID.randomUUID().toString(), System.currentTimeMillis(), type, data);
    forwardToWebSocket(sessionId, "server_event", JsonObject.mapFrom(event));
  }

  void forwardToWebSocket(String sessionId, String method, Object params) {
    if (sessionId == null) return;
    Set<ServerWebSocket> sockets = sessions.getSockets(sessionId);
    if (sockets != null) {
      String payload = JsonObject.mapFrom(JsonRpcNotification.create(method, params)).encode();
      sockets.forEach(
          ws -> {
            if (!ws.isClosed()) {
              ws.writeTextMessage(payload);
            }
          });
    }
  }
}
