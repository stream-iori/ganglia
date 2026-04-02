package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.CompactBoundaryMetadata;
import work.ganglia.port.chat.CompactBoundaryTurn;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.prompt.CompressionBudget;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.port.internal.prompt.SessionMemoryCompactConfig;
import work.ganglia.port.internal.state.ContextOptimizationStep;
import work.ganglia.port.internal.state.FileRestorationService;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.state.OptimizationContext;
import work.ganglia.port.internal.state.OptimizationResult;
import work.ganglia.util.TokenCounter;

/**
 * Core compression step that summarizes old turns to reduce context size.
 *
 * <p>This step is triggered when context exceeds the compression threshold. It:
 *
 * <ol>
 *   <li>Calculates how many turns to keep based on target tokens
 *   <li>Compresses the remaining turns using LLM summarization
 *   <li>Handles chunked compression for large turn sets
 *   <li>Creates compact boundary markers for chain linking
 *   <li>Optionally restores recently accessed files
 * </ol>
 *
 * <p>Priority: 30 (executed after microcompact and slimming)
 */
public class CompressionStep implements ContextOptimizationStep {
  private static final Logger logger = LoggerFactory.getLogger(CompressionStep.class);

  /** Circuit breaker: max consecutive compression failures before giving up. */
  private static final int MAX_CONSECUTIVE_FAILURES = 3;

  private final ModelConfigProvider modelConfig;
  private final AgentConfigProvider agentConfig;
  private final ContextCompressor compressor;
  private final TokenCounter tokenCounter;
  private final ObservationDispatcher dispatcher;
  private final ContextBudget budget;
  private final CompressionBudget compressionBudget;
  private final SessionMemoryCompactConfig sessionMemoryConfig;
  private final FileRestorationService fileRestorationService;
  private final ChunkedCompressor chunkedCompressor;
  private final PTLRetryHandler retryHandler;

  public CompressionStep(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget,
      CompressionBudget compressionBudget,
      FileRestorationService fileRestorationService) {
    this(
        modelConfig,
        agentConfig,
        compressor,
        tokenCounter,
        dispatcher,
        budget,
        compressionBudget,
        SessionMemoryCompactConfig.defaults(),
        fileRestorationService);
  }

  public CompressionStep(
      ModelConfigProvider modelConfig,
      AgentConfigProvider agentConfig,
      ContextCompressor compressor,
      TokenCounter tokenCounter,
      ObservationDispatcher dispatcher,
      ContextBudget budget,
      CompressionBudget compressionBudget,
      SessionMemoryCompactConfig sessionMemoryConfig,
      FileRestorationService fileRestorationService) {
    this.modelConfig = modelConfig;
    this.agentConfig = agentConfig;
    this.compressor = compressor;
    this.tokenCounter = tokenCounter;
    this.dispatcher = dispatcher;
    this.budget = budget;
    this.compressionBudget = compressionBudget;
    this.sessionMemoryConfig =
        sessionMemoryConfig != null ? sessionMemoryConfig : SessionMemoryCompactConfig.defaults();
    this.fileRestorationService = fileRestorationService;
    this.chunkedCompressor = new ChunkedCompressor(compressor, tokenCounter, compressionBudget);
    this.retryHandler = new PTLRetryHandler(compressor, tokenCounter);
  }

  @Override
  public String name() {
    return "Compression";
  }

  @Override
  public int priority() {
    return 30; // Execute after microcompact and slimming
  }

  @Override
  public boolean shouldApply(SessionContext context, OptimizationContext optContext) {
    // Circuit breaker: skip if too many consecutive failures
    if (context.getConsecutiveCompressionFailures() >= MAX_CONSECUTIVE_FAILURES) {
      logger.warn(
          "Circuit breaker: {} consecutive failures, skipping compression",
          context.getConsecutiveCompressionFailures());
      return false;
    }

    // Apply if exceeding threshold
    // For forced compression (exceedsForceLimit), we bypass the "size > 1" guard
    if (optContext.exceedsForceLimit() && !context.previousTurns().isEmpty()) {
      return true;
    }

    // Normal compression: need threshold exceeded and at least 2 turns
    return optContext.exceedsThreshold() && context.previousTurns().size() > 1;
  }

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    // Check for forced compression
    boolean forced = optContext.exceedsForceLimit();

    if (forced) {
      logger.warn(
          "Forced compression triggered ({} > {}). Compressing aggressively.",
          optContext.currentTokens(),
          optContext.forceLimit());
    }

    int turnsToKeep = calculateTurnsToKeep(context, !forced);
    String spanId = "compress-" + java.util.UUID.randomUUID().toString().substring(0, 8);

    // Emit compression started event
    if (dispatcher != null) {
      Map<String, Object> startData = new HashMap<>();
      startData.put("beforeTokens", optContext.currentTokens());
      startData.put("contextLimit", optContext.contextLimit());
      startData.put("compressionTarget", budget != null ? budget.compressionTarget() : -1);
      dispatcher.dispatch(
          context.sessionId(),
          ObservationType.CONTEXT_COMPRESSED,
          "compression_started",
          startData,
          spanId,
          null);
    }

    return compressSession(context, turnsToKeep, forced)
        .map(
            result -> {
              int afterTokens =
                  result.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();

              logger.info(
                  "Compression complete: {} -> {} tokens", optContext.currentTokens(), afterTokens);

              // Emit completion event
              if (dispatcher != null) {
                Map<String, Object> finishData = new HashMap<>();
                finishData.put("beforeTokens", optContext.currentTokens());
                finishData.put("afterTokens", afterTokens);
                dispatcher.dispatch(
                    context.sessionId(),
                    ObservationType.SYSTEM_EVENT,
                    "compression_finished",
                    finishData,
                    spanId,
                    null);
              }

              int tokensSaved =
                  optContext.currentTokens() - afterTokens - optContext.systemOverheadTokens();
              return OptimizationResult.changed(
                  result.resetCompressionFailures(), Math.max(0, tokensSaved));
            })
        .recover(
            error -> {
              logger.error("Compression failed: {}", error.getMessage());
              // Increment failure counter
              return Future.succeededFuture(
                  OptimizationResult.changed(context.withCompressionFailure(), 0));
            });
  }

  /**
   * Compresses a session by summarizing old turns.
   *
   * @param context the session context
   * @param turnsToKeep number of recent turns to preserve
   * @param forced whether this is a forced compression
   * @return a future containing the compressed context
   */
  private Future<SessionContext> compressSession(
      SessionContext context, int turnsToKeep, boolean forced) {
    List<Turn> allPrevious = context.previousTurns();
    if (allPrevious.size() <= turnsToKeep) {
      return Future.succeededFuture(context);
    }

    int compressCount = allPrevious.size() - turnsToKeep;
    List<Turn> toCompress = new ArrayList<>(allPrevious.subList(0, compressCount));
    List<Turn> toKeep = new ArrayList<>(allPrevious.subList(compressCount, allPrevious.size()));

    int utilityContextLimit = modelConfig.getUtilityContextLimit();

    // Check if we should use session memory compact
    if (shouldUseSessionMemoryCompact(context, toCompress)) {
      String runningSummary = context.getRunningSummary();
      if (runningSummary != null && !runningSummary.isBlank()) {
        logger.info("Using running summary for compression (session memory compact)");
        return Future.succeededFuture(
            buildCompressedContext(context, runningSummary, toKeep, toCompress.size(), forced));
      }
    }

    // Fallback: if running summary exists but session memory compact conditions not met, still use
    // it
    String runningSummary = context.getRunningSummary();
    if (runningSummary != null && !runningSummary.isBlank()) {
      logger.info("Using running summary for compression (skipping full LLM compress)");
      return Future.succeededFuture(
          buildCompressedContext(context, runningSummary, toKeep, toCompress.size(), forced));
    }

    // Use chunked compressor
    return chunkedCompressor
        .compress(toCompress, utilityContextLimit)
        .map(
            summary -> buildCompressedContext(context, summary, toKeep, toCompress.size(), forced));
  }

  /** Calculates how many previous turns to keep after compression. */
  private int calculateTurnsToKeep(SessionContext context, boolean guaranteeOne) {
    int limit = modelConfig.getContextLimit();
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

  /** Checks if session memory compact should be used. */
  private boolean shouldUseSessionMemoryCompact(SessionContext context, List<Turn> toCompress) {
    SessionMemoryCompactConfig config = sessionMemoryConfig;

    if (!context.hasValidRunningSummary()) {
      return false;
    }

    int textBlockCount = 0;
    int estimatedTokens = 0;

    for (Turn t : toCompress) {
      if (t.hasTextContent()) {
        textBlockCount++;
      }
      estimatedTokens += t.estimateTokens(tokenCounter);
    }

    return textBlockCount >= config.minTextBlockMessages()
        && estimatedTokens >= config.minTokens()
        && estimatedTokens <= config.maxTokens();
  }

  /** Builds the compressed context with summary turn. */
  private SessionContext buildCompressedContext(
      SessionContext context,
      String summary,
      List<Turn> toKeep,
      int turnsCompressed,
      boolean forced) {

    // Find previous boundary for chain linking
    String previousBoundaryId = context.findLastCompactBoundary().map(Turn::id).orElse(null);

    // Create summary message
    Message summaryMsg = Message.system("SUMMARY OF PREVIOUS INTERACTIONS:\n" + summary);
    Turn summaryTurn = Turn.newTurn("summary-" + System.currentTimeMillis(), summaryMsg);

    // Calculate tokens
    int preTokens =
        context.previousTurns().stream()
            .flatMap(t -> t.flatten().stream())
            .mapToInt(m -> m.countTokens(tokenCounter))
            .sum();
    int postTokens =
        summaryMsg.countTokens(tokenCounter)
            + toKeep.stream()
                .flatMap(t -> t.flatten().stream())
                .mapToInt(m -> m.countTokens(tokenCounter))
                .sum();

    // Create compact boundary turn
    CompactBoundaryMetadata boundaryMeta =
        forced
            ? CompactBoundaryMetadata.forced(
                preTokens, postTokens, turnsCompressed, previousBoundaryId)
            : CompactBoundaryMetadata.auto(
                preTokens, postTokens, turnsCompressed, previousBoundaryId);
    CompactBoundaryTurn boundaryTurn = CompactBoundaryTurn.create(boundaryMeta);

    List<Turn> newPrevious = new ArrayList<>();
    newPrevious.add(boundaryTurn.toTurn());
    newPrevious.add(summaryTurn);
    newPrevious.addAll(toKeep);

    SessionContext compressed = context.withPreviousTurns(newPrevious);

    // Post-compact file restoration
    if (fileRestorationService != null) {
      List<Message> restoredFiles =
          fileRestorationService
              .restoreRecentFiles(
                  compressed,
                  agentConfig.getPostCompactMaxFiles(),
                  agentConfig.getPostCompactTokenBudget(),
                  agentConfig.getPostCompactMaxTokensPerFile())
              .result();

      if (restoredFiles != null && !restoredFiles.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        sb.append("Recently accessed files (re-read after compression):\n\n");
        for (Message msg : restoredFiles) {
          sb.append(msg.content()).append("\n\n");
        }
        Turn restorationTurn =
            Turn.newTurn(
                "restoration-" + System.currentTimeMillis(), Message.system(sb.toString().trim()));

        List<Turn> withRestoration = new ArrayList<>();
        withRestoration.add(boundaryTurn.toTurn());
        withRestoration.add(restorationTurn);
        withRestoration.add(summaryTurn);
        withRestoration.addAll(toKeep);

        compressed = context.withPreviousTurns(withRestoration);
        logger.info("Restored {} files after compression", restoredFiles.size());
      }
    }

    return compressed;
  }
}
