package work.ganglia.infrastructure.internal.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.MicrocompactConfig;
import work.ganglia.port.internal.state.ContextOptimizationStep;
import work.ganglia.port.internal.state.OptimizationContext;
import work.ganglia.port.internal.state.OptimizationResult;
import work.ganglia.util.TokenCounter;

/**
 * Optimization step that performs time-based microcompact.
 *
 * <p>Clears old tool results when the gap since the last assistant message exceeds a threshold.
 * This optimization is based on the observation that LLM providers have a prompt cache TTL
 * (typically ~60 minutes). After this gap, the cache is cold anyway, so clearing old tool results
 * doesn't lose any caching benefit but saves tokens.
 *
 * <p>Priority: 10 (executed first, before slimming and compression)
 */
public class TimeBasedMicrocompactStep implements ContextOptimizationStep {
  private static final Logger logger = LoggerFactory.getLogger(TimeBasedMicrocompactStep.class);

  private final ToolResultCompactor compactor;
  private final MicrocompactConfig config;
  private final TokenCounter tokenCounter;

  public TimeBasedMicrocompactStep(
      ToolResultCompactor compactor, MicrocompactConfig config, TokenCounter tokenCounter) {
    this.compactor = compactor;
    this.config = config;
    this.tokenCounter = tokenCounter;
  }

  @Override
  public String name() {
    return "TimeBasedMicrocompact";
  }

  @Override
  public int priority() {
    return 10; // Execute first
  }

  @Override
  public boolean shouldApply(SessionContext context, OptimizationContext optContext) {
    // Only apply if enabled and there are previous turns
    return config.enabled() && !context.previousTurns().isEmpty();
  }

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    SessionContext result =
        compactor.compactByTimeGap(
            context,
            config.gapThresholdMinutes(),
            config.keepRecent(),
            ToolResultCompactor.DEFAULT_COMPACTABLE_TOOLS);

    if (result == context) {
      return Future.succeededFuture(OptimizationResult.unchanged(context));
    }

    // Calculate tokens saved
    int tokensSaved =
        optContext.historyTokens()
            - result.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();

    return Future.succeededFuture(OptimizationResult.changed(result, Math.max(0, tokensSaved)));
  }
}
