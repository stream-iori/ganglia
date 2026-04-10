package work.ganglia.trading.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.web.model.TradingServerEvent;

/** Manages WebSocket sessions, event caching, trace clients, and signal history. */
class TradingSessionManager {
  private final List<TradingServerEvent.SignalHistoryEntry> signalHistory =
      Collections.synchronizedList(new ArrayList<>());
  private final Map<String, List<JsonObject>> sessionHistories = new ConcurrentHashMap<>();
  private final Map<String, Set<ServerWebSocket>> sessionSockets = new ConcurrentHashMap<>();
  private final Set<ServerWebSocket> traceClients = ConcurrentHashMap.newKeySet();
  private final Set<String> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

  // ── Socket management ──────────────────────────────────────────────

  void addSocket(String sessionId, ServerWebSocket ws) {
    sessionSockets.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(ws);
  }

  void removeSocket(String sessionId, ServerWebSocket ws) {
    Set<ServerWebSocket> sockets = sessionSockets.get(sessionId);
    if (sockets != null) {
      sockets.remove(ws);
      if (sockets.isEmpty()) sessionSockets.remove(sessionId);
    }
  }

  Set<ServerWebSocket> getSockets(String sessionId) {
    return sessionSockets.get(sessionId);
  }

  // ── Trace clients ──────────────────────────────────────────────────

  void addTraceClient(ServerWebSocket ws) {
    traceClients.add(ws);
  }

  void removeTraceClient(ServerWebSocket ws) {
    traceClients.remove(ws);
  }

  Set<ServerWebSocket> getTraceClients() {
    return traceClients;
  }

  // ── Active sessions ────────────────────────────────────────────────

  boolean isSessionActive(String sessionId) {
    return activeSessions.contains(sessionId);
  }

  void markActive(String sessionId) {
    activeSessions.add(sessionId);
  }

  void markInactive(String sessionId) {
    activeSessions.remove(sessionId);
  }

  // ── Event cache ────────────────────────────────────────────────────

  void cacheEvent(String sessionId, JsonObject event) {
    sessionHistories.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(event);
  }

  List<JsonObject> getHistory(String sessionId) {
    return sessionHistories.getOrDefault(sessionId, Collections.emptyList());
  }

  // ── Signal history ─────────────────────────────────────────────────

  void addSignal(TradingServerEvent.SignalHistoryEntry entry) {
    signalHistory.add(entry);
  }

  List<TradingServerEvent.SignalHistoryEntry> getSignalHistory() {
    return signalHistory;
  }
}
