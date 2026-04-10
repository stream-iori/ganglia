package work.ganglia.port.internal.state;

import java.time.Instant;
import java.util.Map;

/**
 * An immutable fact in the Blackboard event-sourced store.
 *
 * @param id Unique fact identifier.
 * @param version Monotonic version for optimistic concurrency.
 * @param summary L1 short text included in LLM context.
 * @param detailRef L2 reference path to cold storage detail.
 * @param sourceManager ID of the manager that published this fact.
 * @param status Current fact status.
 * @param createdAt Timestamp of creation.
 * @param cycleNumber The engine cycle in which this fact was created.
 * @param tags Free-form key-value metadata (e.g., stance, role, phase). Never null.
 */
public record Fact(
    String id,
    int version,
    String summary,
    String detailRef,
    String sourceManager,
    FactStatus status,
    Instant createdAt,
    int cycleNumber,
    Map<String, String> tags) {

  /** Backward-compatible constructor without tags. */
  public Fact(
      String id,
      int version,
      String summary,
      String detailRef,
      String sourceManager,
      FactStatus status,
      Instant createdAt,
      int cycleNumber) {
    this(id, version, summary, detailRef, sourceManager, status, createdAt, cycleNumber, Map.of());
  }
}
