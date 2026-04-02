package work.ganglia.port.internal.state;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;

/**
 * A single step in the context optimization pipeline.
 *
 * <p>Each step performs a specific optimization operation (e.g., microcompact, slimming,
 * compression). Steps are executed in sequence, and each step can decide whether it should apply
 * based on the current context state.
 *
 * <p>This interface enables:
 *
 * <ul>
 *   <li>Pluggable optimization strategies
 *   <li>Independent testing of each step
 *   <li>Configurable pipeline composition
 *   <li>Clear separation of concerns
 * </ul>
 */
public interface ContextOptimizationStep {

  /**
   * Returns the name of this optimization step for logging and debugging.
   *
   * @return the step name
   */
  String name();

  /**
   * Determines whether this step should be applied given the current context.
   *
   * <p>This method should be cheap and not modify any state. It's used by the pipeline to skip
   * unnecessary work.
   *
   * @param context the current session context
   * @param optContext the optimization context with thresholds and limits
   * @return true if this step should be applied
   */
  default boolean shouldApply(SessionContext context, OptimizationContext optContext) {
    return true;
  }

  /**
   * Applies this optimization step to the session context.
   *
   * @param context the current session context
   * @param optContext the optimization context with thresholds and limits
   * @return a future containing the optimization result
   */
  Future<OptimizationResult> apply(SessionContext context, OptimizationContext optContext);

  /**
   * Returns the priority of this step. Lower values are executed first.
   *
   * <p>Default priority is 100. Steps can override to customize ordering.
   *
   * @return the priority value
   */
  default int priority() {
    return 100;
  }
}
