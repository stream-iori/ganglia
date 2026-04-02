package work.ganglia.infrastructure.internal.state;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ContextOptimizationStep;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.state.OptimizationContext;
import work.ganglia.port.internal.state.OptimizationResult;

/**
 * Guard step that checks for hard limit violations.
 *
 * <p>This step runs first (priority 0) and fails immediately if the context exceeds the hard limit,
 * which is a financial guardrail that should never be exceeded.
 *
 * <p>If the context exceeds the hard limit, this step returns a failed future, aborting the entire
 * optimization pipeline.
 *
 * <p>Priority: 0 (executed first, before all other steps)
 */
public class HardLimitGuardStep implements ContextOptimizationStep {
  private static final Logger logger = LoggerFactory.getLogger(HardLimitGuardStep.class);

  private final ObservationDispatcher dispatcher;

  public HardLimitGuardStep(ObservationDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public String name() {
    return "HardLimitGuard";
  }

  @Override
  public int priority() {
    return 0; // Always execute first
  }

  @Override
  public boolean shouldApply(SessionContext context, OptimizationContext optContext) {
    // Always check - no skip condition
    return true;
  }

  @Override
  public Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext) {
    if (optContext.exceedsHardLimit()) {
      logger.error(
          "Session token limit exceeded ({} > {}). Aborting.",
          optContext.currentTokens(),
          optContext.hardLimit());

      // Emit error observation
      if (dispatcher != null) {
        Map<String, Object> errorData = new HashMap<>();
        errorData.put("currentTokens", optContext.currentTokens());
        errorData.put("hardLimit", optContext.hardLimit());
        errorData.put("errorType", "hard_limit_exceeded");
        dispatcher.dispatch(
            context.sessionId(),
            ObservationType.ERROR,
            "Session reached maximum safety token limit",
            errorData);
      }

      return Future.failedFuture(
          "Session reached maximum safety token limit (" + optContext.hardLimit() + ").");
    }

    return Future.succeededFuture(OptimizationResult.unchanged(context));
  }
}
