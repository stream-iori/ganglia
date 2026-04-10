package work.ganglia.trading.graph;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.subagent.TerminationController;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.Fact;

/**
 * Custom {@link TerminationController} for bull/bear debate cycles. Checks Blackboard facts for
 * stance convergence between bull and bear analysts.
 *
 * <p>Decision logic (evaluated in order):
 *
 * <ol>
 *   <li>If validation passed → CONVERGED
 *   <li>If bull and bear stances match → CONVERGED
 *   <li>If no new facts and cycle ≥ stall threshold → STALLED
 *   <li>If cycle number ≥ max debate rounds → BUDGET_EXCEEDED
 *   <li>Otherwise → CONTINUE
 * </ol>
 */
public class DebateTerminationController implements TerminationController {
  private static final Logger logger = LoggerFactory.getLogger(DebateTerminationController.class);

  private final Blackboard blackboard;
  private final int maxDebateRounds;
  private final int stallThreshold;

  public DebateTerminationController(
      Blackboard blackboard, int maxDebateRounds, int stallThreshold) {
    this.blackboard = blackboard;
    this.maxDebateRounds = maxDebateRounds;
    this.stallThreshold = stallThreshold;
  }

  @Override
  public Future<Decision> evaluate(int cycleNumber, CycleResult cycleResult) {
    logger.debug("Evaluating termination at cycle {}", cycleNumber);

    // 1. Validation passed → converged regardless of blackboard state
    if (cycleResult.validationPassed()) {
      logger.info("Debate converged at cycle {} via validation", cycleNumber);
      return Future.succeededFuture(
          new Decision(
              DecisionType.CONVERGED, "Validation passed: " + cycleResult.validationSummary()));
    }

    // 2-6. Check blackboard for stance convergence
    Future<List<Fact>> bullFacts = blackboard.getActiveFacts(Map.of("role", "bull"));
    Future<List<Fact>> bearFacts = blackboard.getActiveFacts(Map.of("role", "bear"));

    return bullFacts
        .compose(
            bulls ->
                bearFacts.map(
                    bears -> {
                      // 2-3. Check stance convergence
                      String bullStance = extractLatestStance(bulls);
                      String bearStance = extractLatestStance(bears);

                      if (bullStance != null
                          && bearStance != null
                          && bullStance.equals(bearStance)) {
                        logger.info(
                            "Debate converged at cycle {}: bull and bear agree on {}",
                            cycleNumber,
                            bullStance);
                        return new Decision(
                            DecisionType.CONVERGED,
                            "Bull and bear agree on direction: " + bullStance);
                      }

                      // Defer remaining checks to fact count query
                      return null;
                    }))
        .compose(
            decision -> {
              if (decision != null) {
                return Future.succeededFuture(decision);
              }

              // 4. Stall detection — check if new facts were produced recently
              return blackboard
                  .getNewFactCount(stallThreshold)
                  .map(
                      count -> {
                        if (count == 0 && cycleNumber >= stallThreshold) {
                          return new Decision(
                              DecisionType.STALLED,
                              "No new facts in last " + stallThreshold + " cycles");
                        }

                        // 5. Budget check
                        if (cycleNumber >= maxDebateRounds) {
                          return new Decision(
                              DecisionType.BUDGET_EXCEEDED,
                              "Max debate rounds reached (" + maxDebateRounds + ")");
                        }

                        // 6. Continue debating
                        return new Decision(
                            DecisionType.CONTINUE,
                            "Bull and bear stances diverge, continuing debate");
                      });
            });
  }

  /**
   * Extracts the stance tag from the most recent fact in the list. Facts are assumed to be ordered
   * by creation time; we pick the last one as the latest.
   */
  private String extractLatestStance(List<Fact> facts) {
    if (facts == null || facts.isEmpty()) {
      return null;
    }
    // Take the fact with the highest cycle number as the latest
    Fact latest =
        facts.stream().reduce((a, b) -> b.cycleNumber() >= a.cycleNumber() ? b : a).orElse(null);
    return latest != null ? latest.tags().get("stance") : null;
  }
}
