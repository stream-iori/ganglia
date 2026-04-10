package work.ganglia.kernel.subagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.state.Blackboard;

/**
 * Monitors cycle count and superseded fact count to decide when the CyclicManagerEngine should
 * insert a summarization step before the next cycle.
 *
 * <p>Trigger conditions (OR logic — either one fires):
 *
 * <ul>
 *   <li>Superseded fact count >= {@code supersededThreshold}
 *   <li>Cycle count >= {@code cycleThreshold}
 * </ul>
 */
public class CycleAwareTrigger {
  private static final Logger logger = LoggerFactory.getLogger(CycleAwareTrigger.class);

  private final Blackboard blackboard;
  private final int supersededThreshold;
  private final int cycleThreshold;

  /**
   * @param blackboard the shared fact store
   * @param supersededThreshold number of superseded facts before triggering (default: 10)
   * @param cycleThreshold number of cycles before triggering (default: 10)
   */
  public CycleAwareTrigger(Blackboard blackboard, int supersededThreshold, int cycleThreshold) {
    this.blackboard = blackboard;
    this.supersededThreshold = supersededThreshold;
    this.cycleThreshold = cycleThreshold;
  }

  /**
   * Checks whether a summarization step should be inserted before the next cycle.
   *
   * @param currentCycle the current cycle number (1-based)
   * @return true if summarization should be triggered
   */
  public Future<Boolean> shouldSummarize(int currentCycle) {
    // Fast path: cycle threshold check (no I/O needed)
    if (currentCycle >= cycleThreshold) {
      logger.info("Summarization triggered by cycle count: {} >= {}", currentCycle, cycleThreshold);
      return Future.succeededFuture(true);
    }

    // Check superseded fact count from blackboard
    return blackboard
        .getSupersededCount()
        .map(
            supersededCount -> {
              if (supersededCount >= supersededThreshold) {
                logger.info(
                    "Summarization triggered by superseded facts: {} >= {}",
                    supersededCount,
                    supersededThreshold);
                return true;
              }
              return false;
            });
  }
}
