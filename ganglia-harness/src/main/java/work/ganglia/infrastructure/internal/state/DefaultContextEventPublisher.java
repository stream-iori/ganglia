package work.ganglia.infrastructure.internal.state;

import java.util.HashMap;
import java.util.Map;

import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.ContextEventPublisher;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * Default implementation of ContextEventPublisher that delegates to ObservationDispatcher.
 *
 * <p>This class adapts the semantic event publishing methods to the generic ObservationDispatcher
 * interface, handling the construction of appropriate data maps.
 */
public class DefaultContextEventPublisher implements ContextEventPublisher {

  private final ObservationDispatcher dispatcher;

  public DefaultContextEventPublisher(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  // ── Compression Events ─────────────────────────────────────────────────────

  @Override
  public void publishCompressionStarted(String sessionId, int beforeTokens) {
    Map<String, Object> data = new HashMap<>();
    data.put("beforeTokens", beforeTokens);
    data.put("status", "started");
    dispatcher.dispatch(sessionId, ObservationType.CONTEXT_COMPRESSED, "compression_started", data);
  }

  @Override
  public void publishCompressionFinished(
      String sessionId, int beforeTokens, int afterTokens, boolean success) {
    Map<String, Object> data = new HashMap<>();
    data.put("beforeTokens", beforeTokens);
    data.put("afterTokens", afterTokens);
    data.put("savedTokens", beforeTokens - afterTokens);
    data.put("success", success);
    data.put("status", success ? "success" : "failed");
    dispatcher.dispatch(sessionId, ObservationType.SYSTEM_EVENT, "compression_finished", data);
  }

  // ── Pressure Events ────────────────────────────────────────────────────────

  @Override
  public void publishPressureChanged(String sessionId, String level, int currentTokens, int limit) {
    Map<String, Object> data = new HashMap<>();
    data.put("level", level);
    data.put("currentTokens", currentTokens);
    data.put("limit", limit);
    data.put("usagePercent", limit > 0 ? (currentTokens * 100.0 / limit) : 0);
    dispatcher.dispatch(
        sessionId,
        ObservationType.CONTEXT_PRESSURE_CHANGED,
        "context_pressure_" + level.toLowerCase(),
        data);
  }

  // ── Budget Events ───────────────────────────────────────────────────────────

  @Override
  public void publishBudgetAllocated(String sessionId, ContextBudget budget) {
    Map<String, Object> data = new HashMap<>();
    data.put("contextLimit", budget.contextLimit());
    data.put("maxGenerationTokens", budget.maxGenerationTokens());
    data.put("systemPrompt", budget.systemPrompt());
    data.put("history", budget.history());
    data.put("currentTurnBudget", budget.currentTurnBudget());
    data.put("toolOutputPerMessage", budget.toolOutputPerMessage());
    data.put("toolOutputAggregate", budget.toolOutputAggregate());
    data.put("observationFallback", budget.observationFallback());
    data.put("compressionTarget", budget.compressionTarget());
    dispatcher.dispatch(
        sessionId, ObservationType.CONTEXT_BUDGET_ALLOCATED, "context_budget_allocated", data);
  }

  // ── Analysis Events ─────────────────────────────────────────────────────────

  @Override
  public void publishContextAnalysis(String sessionId, Map<String, Object> analysis) {
    dispatcher.dispatch(sessionId, ObservationType.CONTEXT_ANALYSIS, "context_analysis", analysis);
  }

  // ── Cache Events ────────────────────────────────────────────────────────────

  @Override
  public void publishPromptCacheStats(String sessionId, Map<String, Object> stats) {
    dispatcher.dispatch(sessionId, ObservationType.PROMPT_CACHE_STATS, "prompt_cache_stats", stats);
  }
}
