package work.ganglia.infrastructure.internal.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.ContextOptimizationStep;
import work.ganglia.port.internal.state.OptimizationContext;
import work.ganglia.port.internal.state.OptimizationResult;
import work.ganglia.util.TokenCounter;

/**
 * Optimization step that performs zero-cost context slimming.
 *
 * <p>Applies three operations in sequence:
 *
 * <ol>
 *   <li>stripOldThinkingBlocks - Remove thinking blocks from old turns
 *   <li>compactOldToolCallArgs - Replace old tool call arguments with summaries
 *   <li>deduplicateSystemMessages - Merge multiple summary turns
 * </ol>
 *
 * <p>This step is "zero-cost" because it doesn't require LLM calls - it just removes or simplifies
 * existing content.
 *
 * <p>Priority: 20 (executed after microcompact, before compression)
 */
public class SlimmingStep implements ContextOptimizationStep {
  private static final Logger logger = LoggerFactory.getLogger(SlimmingStep.class);

  private final ContextSlimmer slimmer;
  private final TokenCounter tokenCounter;

  public SlimmingStep(ContextSlimmer slimmer, TokenCounter tokenCounter) {
    this.slimmer = slimmer;
    this.tokenCounter = tokenCounter;
  }

  @Override
  public String name() {
    return "Slimming";
  }

  @Override
  public int priority() {
    return 20; // Execute after microcompact
  }

  @Override
  public boolean shouldApply(SessionContext context, OptimizationContext optContext) {
    // Only apply if exceeding threshold and there are previous turns
    return optContext.exceedsThreshold() && !context.previousTurns().isEmpty();
  }

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    SessionContext slimmed = slimmer.slimIfNeeded(context);

    if (slimmed == context) {
      return Future.succeededFuture(OptimizationResult.unchanged(context));
    }

    // Calculate tokens saved
    int beforeTokens = optContext.historyTokens();
    int afterTokens = slimmed.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
    int tokensSaved = beforeTokens - afterTokens;

    if (tokensSaved <= 0) {
      return Future.succeededFuture(OptimizationResult.unchanged(context));
    }

    logger.info(
        "Context slimming saved {} tokens ({} -> {})", tokensSaved, beforeTokens, afterTokens);

    // Check if slimming alone was enough to get below threshold
    int newTotal = optContext.currentTokens() - tokensSaved;
    if (newTotal <= optContext.thresholdTokens()) {
      logger.info(
          "Slimming avoided LLM compression ({} -> {} tokens)",
          optContext.currentTokens(),
          newTotal);
      return Future.succeededFuture(OptimizationResult.changed(slimmed, tokensSaved));
    }

    // Still need compression, but continue with slimmed context
    return Future.succeededFuture(OptimizationResult.changed(slimmed, tokensSaved));
  }
}
