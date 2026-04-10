package work.ganglia.kernel.hook.builtin;

import java.util.Set;

/**
 * Decides how {@link ObservationCompressionHook} should handle a tool's output when it exceeds the
 * token budget.
 *
 * <ul>
 *   <li>{@link Action#WRITE_TO_TMP} — write the full raw output to a session-scoped temporary file
 *       and replace the in-memory output with a short path + line-count hint. Used for reproducible
 *       tools whose content can be fetched again on demand. Requires {@code projectRoot} to be
 *       available in the hook; falls back to {@link Action#TRUNCATE_WITH_HINT} otherwise.
 *   <li>{@link Action#TRUNCATE_WITH_HINT} — token-aware truncation with a re-invocation hint; no
 *       file I/O. Fallback when {@code projectRoot} is unavailable.
 *   <li>{@link Action#COMPRESS_AND_STORE} — LLM-summarise the output and persist the full content
 *       in MemoryStore. Used for irreproducible content that cannot be re-generated.
 *   <li>{@link Action#SKIP} — leave the output untouched (e.g. {@code recall_memory}).
 * </ul>
 */
public interface ToolOutputPolicy {

  enum Action {
    WRITE_TO_TMP,
    TRUNCATE_WITH_HINT,
    COMPRESS_AND_STORE,
    SKIP
  }

  Action decide(String toolName);

  // ── Built-in implementations ──────────────────────────────────────────────

  /**
   * Default set of tools whose outputs are reproducible: the full output is written to a session
   * tmp file and the agent receives a short path + line-count hint instead.
   */
  Set<String> DEFAULT_REPRODUCIBLE_TOOLS =
      Set.of(
          "read_file",
          "run_shell_command",
          "bash",
          "list_files",
          "list_directory",
          "search_files",
          "grep_files",
          "find_files");

  /**
   * Default set of tools whose outputs must never be modified. {@code recall_memory} returns
   * content that was already compressed and stored — re-truncating it would corrupt the agent's
   * ability to reference memory entries.
   */
  Set<String> DEFAULT_SKIP_TOOLS = Set.of("recall_memory");

  /**
   * Returns a policy that routes {@code skipToolNames} to {@link Action#SKIP}, {@code
   * reproducibleToolNames} to {@link Action#WRITE_TO_TMP}, and everything else to {@link
   * Action#COMPRESS_AND_STORE}.
   */
  static ToolOutputPolicy of(Set<String> skipToolNames, Set<String> reproducibleToolNames) {
    return toolName -> {
      if (skipToolNames.contains(toolName)) return Action.SKIP;
      if (reproducibleToolNames.contains(toolName)) return Action.WRITE_TO_TMP;
      return Action.COMPRESS_AND_STORE;
    };
  }

  /** Policy using {@link #DEFAULT_SKIP_TOOLS} and {@link #DEFAULT_REPRODUCIBLE_TOOLS}. */
  static ToolOutputPolicy defaultPolicy() {
    return of(DEFAULT_SKIP_TOOLS, DEFAULT_REPRODUCIBLE_TOOLS);
  }
}
