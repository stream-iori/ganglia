package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.state.ContextOptimizer;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.TokenCounter;

public class DefaultContextOptimizer implements ContextOptimizer {
  private static final Logger logger = LoggerFactory.getLogger(DefaultContextOptimizer.class);

  /** Token count above which forced compression is attempted regardless of turn count. */
  static final int FORCE_COMPRESSION_LIMIT = 400_000;

  /** Absolute hard limit — session is aborted if tokens exceed this value. */
  static final int HARD_TOKEN_LIMIT = 500_000;

  private final ModelConfigProvider modelConfig;
  private final AgentConfigProvider agentConfig;
  private final ContextCompressor compressor;
  private final TokenCounter tokenCounter;
  private final ObservationDispatcher dispatcher;
  private final ContextBudget budget;

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter) {
    this(modelConfig, agentConfig, compressor, tokenCounter, null, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher) {
    this(modelConfig, agentConfig, compressor, tokenCounter, dispatcher, null);
  }

  public DefaultContextOptimizer(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget) {
    this.modelConfig = modelConfig;
    this.agentConfig = agentConfig;
    this.compressor = compressor;
    this.tokenCounter = tokenCounter;
    this.dispatcher = dispatcher;
    this.budget = budget;
  }

  @Override
  public Future<SessionContext> optimizeIfNeeded(SessionContext context) {
    return optimizeIfNeeded(context, null);
  }

  @Override
  public Future<SessionContext> optimizeIfNeeded(SessionContext context, String parentSpanId) {
    int historyTokens = context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
    int totalTokens = historyTokens + agentConfig.getSystemOverheadTokens();

    int limit = modelConfig.getContextLimit();
    double threshold = agentConfig.getCompressionThreshold();

    // Hard limit financial guardrail
    if (totalTokens > HARD_TOKEN_LIMIT) {
      logger.error("Session token limit exceeded ({}). Aborting.", totalTokens);
      return Future.failedFuture(
          "Session reached maximum safety token limit (" + HARD_TOKEN_LIMIT + ").");
    }

    // Forced compression — bypasses previousTurns.size() > 1 guard to prevent hitting the hard
    // limit. Compresses aggressively: turnsToKeep may be 0 so even a single oversized previous
    // turn is replaced with a summary.
    if (totalTokens > FORCE_COMPRESSION_LIMIT && !context.previousTurns().isEmpty()) {
      logger.warn(
          "Forced compression triggered ({} > {}). Compressing aggressively.",
          totalTokens,
          FORCE_COMPRESSION_LIMIT);
      int turnsToKeep = calculateTurnsToKeep(context, false);
      return compressSession(context, turnsToKeep);
    }

    if (totalTokens > limit * threshold && context.previousTurns().size() > 1) {
      logger.info(
          "Context threshold reached ({} > {}). Triggering compression...",
          totalTokens,
          (int) (limit * threshold));

      final String compressSpanId =
          "compress-" + java.util.UUID.randomUUID().toString().substring(0, 8);

      if (dispatcher != null) {
        Map<String, Object> startData = new java.util.HashMap<>();
        startData.put("beforeTokens", totalTokens);
        startData.put("contextLimit", limit);
        startData.put(
            "compressionTarget", budget != null ? budget.compressionTarget() : (int) (limit * 0.5));
        startData.put("historyBudget", budget != null ? budget.history() : -1);
        dispatcher.dispatch(
            context.sessionId(),
            ObservationType.CONTEXT_COMPRESSED,
            "context_compression_started",
            startData,
            compressSpanId,
            parentSpanId);
      }
      final int beforeTokens = totalTokens;
      int turnsToKeep = calculateTurnsToKeep(context);
      return compressSession(context, turnsToKeep)
          .map(
              compressedContext -> {
                int afterTokens =
                    compressedContext.history().stream()
                        .mapToInt(m -> m.countTokens(tokenCounter))
                        .sum();
                logger.info("Compression complete. New token count: {}", afterTokens);
                if (dispatcher != null) {
                  Map<String, Object> finishData = new java.util.HashMap<>();
                  finishData.put("beforeTokens", beforeTokens);
                  finishData.put("afterTokens", afterTokens);
                  dispatcher.dispatch(
                      compressedContext.sessionId(),
                      ObservationType.SYSTEM_EVENT,
                      "context_compression_finished",
                      finishData,
                      compressSpanId,
                      parentSpanId);
                }
                return compressedContext;
              });
    }

    return Future.succeededFuture(context);
  }

  private int calculateTurnsToKeep(SessionContext context) {
    return calculateTurnsToKeep(context, true);
  }

  /**
   * Calculates how many previous turns to keep after compression.
   *
   * @param guaranteeOne when true, at least 1 turn is always kept (normal compression); when false,
   *     the result may be 0 (forced/aggressive compression).
   */
  private int calculateTurnsToKeep(SessionContext context, boolean guaranteeOne) {
    int limit = modelConfig.getContextLimit();
    // After compression, target ~50% of available budget to leave room for new interactions
    int targetTokens = budget != null ? budget.compressionTarget() : (int) (limit * 0.5);
    List<Turn> turns = context.previousTurns();
    int accumulatedTokens = 0;
    int keep = 0;
    for (int i = turns.size() - 1; i >= 0; i--) {
      int turnTokens =
          turns.get(i).flatten().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
      if (accumulatedTokens + turnTokens > targetTokens) break;
      accumulatedTokens += turnTokens;
      keep++;
    }
    return guaranteeOne ? Math.max(1, keep) : keep;
  }

  private Future<SessionContext> compressSession(SessionContext context, int turnsToKeep) {
    List<Turn> allPrevious = context.previousTurns();
    if (allPrevious.size() <= turnsToKeep) {
      return Future.succeededFuture(context);
    }

    int compressCount = allPrevious.size() - turnsToKeep;
    List<Turn> toCompress = allPrevious.subList(0, compressCount);
    List<Turn> toKeep = new ArrayList<>(allPrevious.subList(compressCount, allPrevious.size()));

    // If a running summary is available, use it directly instead of re-compressing
    String runningSummary = context.getRunningSummary();
    Future<String> summaryFuture;
    if (runningSummary != null && !runningSummary.isBlank()) {
      logger.info("Using running summary for compression (skipping full LLM compress)");
      summaryFuture = Future.succeededFuture(runningSummary);
    } else {
      summaryFuture = compressor.compress(toCompress);
    }

    return summaryFuture.map(
        summary -> {
          Message summaryMsg = Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + summary);
          Turn summaryTurn = Turn.newTurn("summary-" + System.currentTimeMillis(), summaryMsg);

          List<Turn> newPrevious = new ArrayList<>();
          newPrevious.add(summaryTurn);
          newPrevious.addAll(toKeep);

          return context.withPreviousTurns(newPrevious);
        });
  }
}
