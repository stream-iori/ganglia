package work.ganglia.trading.web.model;

import java.util.Map;

import work.ganglia.trajectory.event.EventType;

/** Generic event sent from the trading server to WebSocket clients. */
public record TradingServerEvent(String eventId, long timestamp, EventType type, Object data) {

  public record PipelinePhaseData(String phase, String status, String ticker) {}

  public record PipelineCompletedData(
      String signal,
      double confidence,
      String rationale,
      String perceptionReport,
      String debateReport,
      String riskReport) {}

  public record DebateCycleData(
      String debateType, int cycleNumber, int maxCycles, String decisionType) {}

  public record FactEventData(
      String factId,
      String summary,
      String sourceManager,
      int cycleNumber,
      Map<String, String> tags) {}

  public record FactSupersededData(String factId, String reason) {}

  public record SignalHistoryEntry(
      String ticker, String signal, double confidence, String rationale, long timestamp) {}

  public record TradingInitConfigData(String ticker, Object config) {}
}
