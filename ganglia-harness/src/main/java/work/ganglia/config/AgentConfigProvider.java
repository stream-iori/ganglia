package work.ganglia.config;

import work.ganglia.port.internal.prompt.MicrocompactConfig;
import work.ganglia.port.internal.prompt.SessionMemoryCompactConfig;

/** Interface Segregation: Provides Agent execution and project configuration. */
public interface AgentConfigProvider {

  // ── Default values (single source of truth for fallbacks) ─────────────
  int DEFAULT_MAX_ITERATIONS = 10;
  double DEFAULT_COMPRESSION_THRESHOLD = 0.7;
  long DEFAULT_TOOL_TIMEOUT_MS = 120_000;
  int DEFAULT_SYSTEM_OVERHEAD_TOKENS = 6000;
  double DEFAULT_FORCE_COMPRESSION_MULTIPLIER = 3.0;
  double DEFAULT_HARD_LIMIT_MULTIPLIER = 4.0;
  long DEFAULT_CACHE_EXPIRY_MS = 5 * 60 * 1000L; // 5 minutes

  // ── Post-compact restoration defaults ───────────────────────────────────
  int DEFAULT_POST_COMPACT_MAX_FILES = 5;
  int DEFAULT_POST_COMPACT_TOKEN_BUDGET = 50_000;
  int DEFAULT_POST_COMPACT_MAX_TOKENS_PER_FILE = 5_000;

  int getMaxIterations();

  double getCompressionThreshold();

  String getProjectRoot();

  String getInstructionFile();

  /** Maximum time in milliseconds a single tool execution may run before being timed out. */
  default long getToolTimeoutMs() {
    return DEFAULT_TOOL_TIMEOUT_MS;
  }

  /**
   * Estimated token overhead for system prompt, tool definitions, and protocol framing. This is
   * added to the history token count when checking the compression threshold.
   */
  default int getSystemOverheadTokens() {
    return DEFAULT_SYSTEM_OVERHEAD_TOKENS;
  }

  /** Multiplier of contextLimit at which forced compression is triggered (default 3.0×). */
  default double getForceCompressionMultiplier() {
    return DEFAULT_FORCE_COMPRESSION_MULTIPLIER;
  }

  /** Multiplier of contextLimit at which the session is aborted (default 4.0×). */
  default double getHardLimitMultiplier() {
    return DEFAULT_HARD_LIMIT_MULTIPLIER;
  }

  /**
   * Time in milliseconds after which the LLM provider's prompt cache is assumed to have expired.
   * Old tool results older than this may be proactively cleared to save tokens.
   */
  default long getCacheExpiryMs() {
    return DEFAULT_CACHE_EXPIRY_MS;
  }

  // ── Time-based Microcompact Configuration ───────────────────────────────

  /** Returns the configuration for time-based microcompact. */
  default MicrocompactConfig getMicrocompactConfig() {
    return MicrocompactConfig.defaults();
  }

  // ── Session Memory Compact Configuration ────────────────────────────────

  /** Returns the configuration for session memory based compression. */
  default SessionMemoryCompactConfig getSessionMemoryCompactConfig() {
    return SessionMemoryCompactConfig.defaults();
  }

  // ── Post-compact Restoration Configuration ──────────────────────────────

  /** Maximum number of files to restore after compression. */
  default int getPostCompactMaxFiles() {
    return DEFAULT_POST_COMPACT_MAX_FILES;
  }

  /** Total token budget for file restoration after compression. */
  default int getPostCompactTokenBudget() {
    return DEFAULT_POST_COMPACT_TOKEN_BUDGET;
  }

  /** Maximum tokens per file for restoration after compression. */
  default int getPostCompactMaxTokensPerFile() {
    return DEFAULT_POST_COMPACT_MAX_TOKENS_PER_FILE;
  }
}
