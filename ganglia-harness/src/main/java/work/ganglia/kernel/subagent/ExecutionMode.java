package work.ganglia.kernel.subagent;

/** Defines how a Manager node executes its work. */
public enum ExecutionMode {
  /** Single agent handles the task directly. */
  SELF,
  /** Spawns a sub-graph of agents to handle the task. */
  DELEGATE
}
