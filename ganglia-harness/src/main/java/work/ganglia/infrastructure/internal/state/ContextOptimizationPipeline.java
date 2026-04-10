package work.ganglia.infrastructure.internal.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.ContextOptimizationStep;
import work.ganglia.port.internal.state.OptimizationContext;
import work.ganglia.util.TokenCounter;

/**
 * Orchestrates the execution of context optimization steps.
 *
 * <p>The pipeline executes steps in priority order (lowest first). Each step can:
 *
 * <ul>
 *   <li>Skip itself if conditions aren't met
 *   <li>Modify the context and pass it to subsequent steps
 *   <li>Request early termination by returning skipRemaining=true
 * </ul>
 */
public class ContextOptimizationPipeline {
  private static final Logger logger = LoggerFactory.getLogger(ContextOptimizationPipeline.class);

  private final List<ContextOptimizationStep> steps;
  private final TokenCounter tokenCounter;
  private final int systemOverheadTokens;

  private ContextOptimizationPipeline(
      List<ContextOptimizationStep> steps, TokenCounter tokenCounter, int systemOverheadTokens) {
    this.steps = steps;
    this.tokenCounter = tokenCounter;
    this.systemOverheadTokens = systemOverheadTokens;
  }

  /**
   * Creates a new pipeline builder.
   *
   * @param tokenCounter the token counter for calculating token usage
   * @return a new builder
   */
  public static Builder builder(TokenCounter tokenCounter) {
    return new Builder(tokenCounter);
  }

  /**
   * Executes the pipeline, applying each step in sequence.
   *
   * @param context the session context to optimize
   * @param contextLimit the model's context limit
   * @param threshold the compression threshold (0.0-1.0)
   * @param forceLimit the force compression limit
   * @param hardLimit the hard limit for session abort
   * @return a future containing the optimized context
   */
  public Future<SessionContext> execute(
      SessionContext context, int contextLimit, double threshold, int forceLimit, int hardLimit) {

    OptimizationContext optContext =
        createOptContext(context, contextLimit, threshold, forceLimit, hardLimit);

    return executeSteps(context, optContext, 0);
  }

  private OptimizationContext createOptContext(
      SessionContext context, int contextLimit, double threshold, int forceLimit, int hardLimit) {

    int historyTokens = context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
    int totalTokens = historyTokens + systemOverheadTokens;

    // threshold is expected as percentage (e.g., 70 for 70%)
    int thresholdPercent = (int) (threshold * 100);
    return new OptimizationContext(
        totalTokens,
        contextLimit,
        thresholdPercent,
        forceLimit,
        hardLimit,
        historyTokens,
        systemOverheadTokens);
  }

  private Future<SessionContext> executeSteps(
      SessionContext context, OptimizationContext optContext, int stepIndex) {

    if (stepIndex >= steps.size()) {
      return Future.succeededFuture(context);
    }

    ContextOptimizationStep step = steps.get(stepIndex);

    // Check if step should apply
    if (!step.shouldApply(context, optContext)) {
      logger.debug("Skipping step {} (conditions not met)", step.name());
      return executeSteps(context, optContext, stepIndex + 1);
    }

    logger.debug("Executing optimization step: {}", step.name());

    return step.apply(context, optContext)
        .compose(
            result -> {
              if (result.skipRemaining()) {
                logger.debug("Step {} requested early termination", step.name());
                return Future.succeededFuture(result.context());
              }

              if (result.changed()) {
                // Recalculate tokens for subsequent steps
                OptimizationContext newOptContext = recalculateTokens(result.context(), optContext);
                return executeSteps(result.context(), newOptContext, stepIndex + 1);
              }

              return executeSteps(context, optContext, stepIndex + 1);
            });
  }

  private OptimizationContext recalculateTokens(
      SessionContext context, OptimizationContext original) {

    int historyTokens = context.history().stream().mapToInt(m -> m.countTokens(tokenCounter)).sum();
    int totalTokens = historyTokens + systemOverheadTokens;

    return original.withTokens(historyTokens, totalTokens);
  }

  /** Builder for creating optimization pipelines. */
  public static class Builder {
    private final TokenCounter tokenCounter;
    private final List<ContextOptimizationStep> steps = new ArrayList<>();
    private int systemOverheadTokens = 6000;

    Builder(TokenCounter tokenCounter) {
      this.tokenCounter = tokenCounter;
    }

    /** Sets the system overhead tokens. */
    public Builder systemOverheadTokens(int tokens) {
      this.systemOverheadTokens = tokens;
      return this;
    }

    /** Adds an optimization step. */
    public Builder addStep(ContextOptimizationStep step) {
      this.steps.add(step);
      return this;
    }

    /** Builds the pipeline with steps sorted by priority. */
    public ContextOptimizationPipeline build() {
      List<ContextOptimizationStep> sorted = new ArrayList<>(steps);
      sorted.sort(Comparator.comparingInt(ContextOptimizationStep::priority));
      return new ContextOptimizationPipeline(sorted, tokenCounter, systemOverheadTokens);
    }
  }
}
