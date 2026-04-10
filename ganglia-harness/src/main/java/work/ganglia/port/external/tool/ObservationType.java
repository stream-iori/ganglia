package work.ganglia.port.external.tool;

/** Types of observations that can occur during the agent loop. */
public enum ObservationType {
  /** A new session has been initiated. */
  SESSION_STARTED,

  /** A new turn has started. */
  TURN_STARTED,

  /** Reasoning phase has started. */
  REASONING_STARTED,

  /** The LLM request (messages, tools, options) has been prepared and is ready to be sent. */
  REQUEST_PREPARED,

  /** A token has been received from the model (for streaming). */
  TOKEN_RECEIVED,

  /** Reasoning phase has finished. */
  REASONING_FINISHED,

  /** A tool execution has started. */
  TOOL_STARTED,

  /** Intermediate output stream from a tool (e.g. TTY line). */
  TOOL_OUTPUT_STREAM,

  /** A tool execution has finished. */
  TOOL_FINISHED,

  /** A tool requires user interaction. */
  USER_INTERACTION_REQUIRED,

  /** The turn has completed. */
  TURN_FINISHED,

  /** A session has concluded (normal completion, abort, or error). */
  SESSION_ENDED,

  /** An error occurred in the loop. */
  ERROR,

  /** A system-level event like context compression. */
  SYSTEM_EVENT,

  /** The task plan (ToDoList) has been updated. */
  PLAN_UPDATED,

  /** Skill tool call started (list_available_skills, activate_skill, or skill-provided tools). */
  SKILL_STARTED,

  /** Skill tool call finished. */
  SKILL_FINISHED,

  /** MCP tool call started. */
  MCP_CALL_STARTED,

  /** MCP tool call finished. */
  MCP_CALL_FINISHED,

  /** Session aborted (stop() called or AbortedException propagated). */
  SESSION_ABORTED,

  /** ContextOptimizer triggered context compression. */
  CONTEXT_COMPRESSED,

  /** Model API call started (before HTTP request). */
  MODEL_CALL_STARTED,

  /** Model API call finished (after response or error). */
  MODEL_CALL_FINISHED,

  /** Token usage recorded for a model call. */
  TOKEN_USAGE_RECORDED,

  /** Memory system completed processing an event. */
  MEMORY_UPDATED,

  /** ContextBudget has been computed and allocated for a session. */
  CONTEXT_BUDGET_ALLOCATED,

  /** Context pressure level changed (NORMAL -> WARNING -> CRITICAL -> BLOCKING). */
  CONTEXT_PRESSURE_CHANGED,

  /** Prompt cache statistics (hit/miss, token counts). */
  PROMPT_CACHE_STATS,

  /** Context analysis with token distribution breakdown. */
  CONTEXT_ANALYSIS,

  /** A new fact has been published to the Blackboard. */
  FACT_PUBLISHED,

  /** A fact has been superseded on the Blackboard. */
  FACT_SUPERSEDED,

  /** A fact has been archived after summarization. */
  FACT_ARCHIVED,

  /** A new manager cycle has started in the CyclicManagerEngine. */
  MANAGER_CYCLE_STARTED,

  /** A manager cycle has finished in the CyclicManagerEngine. */
  MANAGER_CYCLE_FINISHED,

  /** All validations passed — the manager graph has converged. */
  MANAGER_GRAPH_CONVERGED,

  /** No progress detected — the manager graph has stalled. */
  MANAGER_GRAPH_STALLED,

  /** RealityAnchor validation suite started. */
  REALITY_ANCHOR_STARTED,

  /** All RealityAnchor validation suites passed. */
  REALITY_ANCHOR_PASSED,

  /** One or more RealityAnchor validation suites failed. */
  REALITY_ANCHOR_FAILED,

  /** A git worktree has been created for a manager node. */
  WORKTREE_CREATED,

  /** A worktree has been successfully merged into the target branch. */
  WORKTREE_MERGE_SUCCESS,

  /** A merge conflict was detected when merging a worktree. */
  WORKTREE_MERGE_CONFLICT,

  /** A worktree has been cleaned up (removed). */
  WORKTREE_CLEANUP,

  /** Task fingerprint matched a cached result — execution skipped. */
  FINGERPRINT_CACHE_HIT,

  /** Task fingerprint did not match any cached result — executing normally. */
  FINGERPRINT_CACHE_MISS,

  /** Fact detail content written to L2 cold storage. */
  COLD_STORAGE_WRITTEN,

  /** Fact detail content read from L2 cold storage. */
  COLD_STORAGE_READ
}
