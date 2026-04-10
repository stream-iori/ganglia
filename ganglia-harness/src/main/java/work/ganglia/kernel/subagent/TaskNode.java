package work.ganglia.kernel.subagent;

import java.util.List;
import java.util.Map;

/**
 * Represents a single task to be executed by a Sub-Agent.
 *
 * @param id Unique identifier for the node.
 * @param task Description of the sub-task.
 * @param persona Persona to use (e.g., INVESTIGATOR, REFACTORER, GENERAL).
 * @param dependencies List of task IDs that must complete before this one starts.
 * @param inputMapping Optional mapping to specify which dependency outputs should be used as input
 *     for this task.
 * @param missionContext Global mission alignment context propagated to sub-agents.
 * @param mode Execution mode: SELF (single agent) or DELEGATE (spawn sub-graph).
 * @param isolation Isolation level: NONE, SESSION, or WORKTREE.
 */
public record TaskNode(
    String id,
    String task,
    String persona,
    List<String> dependencies,
    Map<String, String> inputMapping,
    String missionContext,
    ExecutionMode mode,
    IsolationLevel isolation) {

  /** Backward-compatible constructor for existing callers (5-arg). */
  public TaskNode(
      String id,
      String task,
      String persona,
      List<String> dependencies,
      Map<String, String> inputMapping) {
    this(
        id,
        task,
        persona,
        dependencies,
        inputMapping,
        null,
        ExecutionMode.SELF,
        IsolationLevel.NONE);
  }
}
