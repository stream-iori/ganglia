package work.ganglia.port.internal.state;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;

/**
 * Append-only, event-sourced fact store for cross-cycle state sharing between Managers.
 *
 * <p>All mutations go through this port; direct state access is not allowed. L1 (active summaries)
 * are kept in context; L2 (detail) is cold storage retrieved on-demand.
 */
public interface Blackboard {

  /**
   * Publishes a new fact.
   *
   * @param managerId the publishing manager's ID
   * @param summary L1 short summary (included in LLM context)
   * @param detailRef L2 reference path to cold storage detail (nullable)
   * @param cycleNumber the current engine cycle number
   * @return the created Fact
   */
  /**
   * Publishes a new fact with metadata tags.
   *
   * @param managerId the publishing manager's ID
   * @param summary L1 short summary (included in LLM context)
   * @param detailRef L2 reference path to cold storage detail (nullable)
   * @param cycleNumber the current engine cycle number
   * @param tags free-form key-value metadata (e.g., stance, role, phase)
   * @return the created Fact
   */
  Future<Fact> publish(
      String managerId,
      String summary,
      String detailRef,
      int cycleNumber,
      Map<String, String> tags);

  /** Publishes a new fact without tags. */
  default Future<Fact> publish(
      String managerId, String summary, String detailRef, int cycleNumber) {
    return publish(managerId, summary, detailRef, cycleNumber, Map.of());
  }

  /**
   * Supersedes an existing fact.
   *
   * @param factId the fact to supersede
   * @param reason why the fact is being superseded
   * @return succeeded future on success, failed future if fact not found or version conflict
   */
  Future<Void> supersede(String factId, String reason);

  /** Returns all non-superseded, non-archived facts (L1 summaries). */
  Future<List<Fact>> getActiveFacts();

  /**
   * Returns active facts filtered by tags. A fact matches if its tags contain all entries in the
   * filter map (subset match).
   */
  default Future<List<Fact>> getActiveFacts(Map<String, String> tagFilter) {
    if (tagFilter == null || tagFilter.isEmpty()) {
      return getActiveFacts();
    }
    return getActiveFacts()
        .map(
            facts ->
                facts.stream()
                    .filter(f -> f.tags().entrySet().containsAll(tagFilter.entrySet()))
                    .toList());
  }

  /** Returns the L2 detail for a specific fact, or null if not found. */
  Future<String> getFactDetail(String factId);

  /** Returns the count of superseded facts (used by CycleAwareTrigger). */
  Future<Integer> getSupersededCount();

  /** Returns the count of new (ACTIVE) facts created in the last N cycles. */
  Future<Integer> getNewFactCount(int lastNCycles);

  /** Returns all superseded facts (for summarization). */
  Future<List<Fact>> getSupersededFacts();

  /**
   * Archives a fact (moves from SUPERSEDED to ARCHIVED status).
   *
   * @param factId the fact to archive
   * @return succeeded future on success, failed future if fact not found
   */
  Future<Void> archive(String factId);
}
