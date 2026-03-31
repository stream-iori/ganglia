package work.ganglia.config;

/** Interface Segregation: Provides Agent execution and project configuration. */
public interface AgentConfigProvider {

  // ── Default values (single source of truth for fallbacks) ─────────────
  int DEFAULT_MAX_ITERATIONS = 10;
  double DEFAULT_COMPRESSION_THRESHOLD = 0.7;
  long DEFAULT_TOOL_TIMEOUT_MS = 120_000;
  int DEFAULT_SYSTEM_OVERHEAD_TOKENS = 6000;
  double DEFAULT_FORCE_COMPRESSION_MULTIPLIER = 3.0;
  double DEFAULT_HARD_LIMIT_MULTIPLIER = 4.0;

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
}
