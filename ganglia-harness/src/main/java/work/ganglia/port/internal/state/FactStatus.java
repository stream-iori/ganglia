package work.ganglia.port.internal.state;

/** Status of a fact in the Blackboard. */
public enum FactStatus {
  /** Fact is current and included in L1 context. */
  ACTIVE,
  /** Fact has been replaced by a newer finding. */
  SUPERSEDED,
  /** Fact has been summarized and moved to cold storage. */
  ARCHIVED
}
