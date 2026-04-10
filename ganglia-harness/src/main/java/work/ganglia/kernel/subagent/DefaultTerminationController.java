package work.ganglia.kernel.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.state.Blackboard;

/**
 * Default termination controller that evaluates cycle results against convergence, budget, and
 * stall conditions.
 *
 * <p>Evaluation order (first match wins):
 *
 * <ol>
 *   <li>Convergence: validation passed → CONVERGED
 *   <li>Budget exceeded: cycleNumber >= maxCycles → BUDGET_EXCEEDED
 *   <li>Stall detection: no new facts in last stallThreshold cycles → STALLED
 *   <li>Otherwise: CONTINUE with failure context
 * </ol>
 */
public class DefaultTerminationController implements TerminationController {
  private static final Logger logger = LoggerFactory.getLogger(DefaultTerminationController.class);

  private final Blackboard blackboard;
  private final int maxCycles;
  private final int stallThreshold;

  /**
   * @param blackboard the shared fact store
   * @param maxCycles hard limit on cycle count (default: 5)
   * @param stallThreshold number of recent cycles to check for new facts (default: 3)
   */
  public DefaultTerminationController(Blackboard blackboard, int maxCycles, int stallThreshold) {
    this.blackboard = blackboard;
    this.maxCycles = maxCycles;
    this.stallThreshold = stallThreshold;
  }

  @Override
  public Future<Decision> evaluate(int cycleNumber, CycleResult cycleResult) {
    // 1. Convergence check: validation passed → done
    if (cycleResult.validationPassed()) {
      logger.info("Cycle {} converged: {}", cycleNumber, cycleResult.validationSummary());
      return Future.succeededFuture(
          new Decision(DecisionType.CONVERGED, cycleResult.validationSummary()));
    }

    // 2. Budget check: hard cycle limit
    if (cycleNumber >= maxCycles) {
      String reason =
          "Budget exceeded: reached max cycles %d. Last validation: %s"
              .formatted(maxCycles, cycleResult.validationSummary());
      logger.warn("Cycle {} budget exceeded (max={})", cycleNumber, maxCycles);
      return Future.succeededFuture(new Decision(DecisionType.BUDGET_EXCEEDED, reason));
    }

    // 3. Stall detection: check if recent cycles produced new facts
    int lookbackCycles = Math.min(stallThreshold, cycleNumber);
    return blackboard
        .getNewFactCount(lookbackCycles)
        .map(
            newFactCount -> {
              if (newFactCount == 0 && cycleNumber >= stallThreshold) {
                String reason =
                    "Stall detected: no new facts in last %d cycles. Last validation: %s"
                        .formatted(stallThreshold, cycleResult.validationSummary());
                logger.warn(
                    "Cycle {} stalled: no new facts in {} cycles", cycleNumber, stallThreshold);
                return new Decision(DecisionType.STALLED, reason);
              }

              // 4. Continue: progress was made, try again
              String reason =
                  "Validation failed but progress detected (%d new facts). Details: %s"
                      .formatted(newFactCount, cycleResult.validationSummary());
              logger.info("Cycle {} continuing: {}", cycleNumber, reason);
              return new Decision(DecisionType.CONTINUE, reason);
            });
  }
}
