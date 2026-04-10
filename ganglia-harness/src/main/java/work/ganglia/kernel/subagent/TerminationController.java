package work.ganglia.kernel.subagent;

import io.vertx.core.Future;

/**
 * Evaluates cycle results and decides whether the CyclicManagerEngine should continue, stop
 * (converged), or abort (budget exceeded / stalled).
 */
public interface TerminationController {

  /** The outcome type of a termination evaluation. */
  enum DecisionType {
    /** All validations passed — the graph has converged. */
    CONVERGED,
    /** Validation failed but progress was made — continue to next cycle. */
    CONTINUE,
    /** Cycle or token budget exceeded — stop with partial result. */
    BUDGET_EXCEEDED,
    /** No new facts produced in the last N cycles — stop with stall diagnosis. */
    STALLED
  }

  /** Result of a single cycle's execution. */
  record CycleResult(boolean validationPassed, String validationSummary) {}

  /** The decision returned by the controller. */
  record Decision(DecisionType type, String reason) {}

  /**
   * Evaluates the current cycle result and returns a decision.
   *
   * @param cycleNumber the 1-based cycle number that just completed
   * @param cycleResult the validation result from this cycle
   * @return a decision on whether to continue, stop, or abort
   */
  Future<Decision> evaluate(int cycleNumber, CycleResult cycleResult);
}
