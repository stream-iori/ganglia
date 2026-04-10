package work.ganglia.kernel.subagent;

/** Defines the isolation strategy for a task node's execution environment. */
public enum IsolationLevel {
  /** No isolation beyond default context scoping. */
  NONE,
  /** Session-level isolation via ContextScoper (lightweight, for read-only tasks). */
  SESSION,
  /** Git worktree isolation (heavyweight, for parallel write tasks). */
  WORKTREE
}
