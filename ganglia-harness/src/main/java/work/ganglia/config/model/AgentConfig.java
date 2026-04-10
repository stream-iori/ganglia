package work.ganglia.config.model;

/** General agent/loop parameters. */
public record AgentConfig(
    int maxIterations,
    double compressionThreshold, // Default 0.7 (70%)
    String projectRoot, // Root directory for file system tools
    String instructionFile, // Default GANGLIA.md
    long toolTimeout, // Max tool execution time in ms (default 120000)
    int observationCompressionThreshold, // Char length above which LLM compression is triggered
    int systemOverheadTokens, // Estimated tokens for system prompt + tool defs + protocol overhead
    double forceCompressionMultiplier, // contextLimit × this = forced compression threshold
    double hardLimitMultiplier, // contextLimit × this = session abort threshold
    String storageBackend // Memory storage backend name (e.g. "sqlite", "filesystem"), null = auto
    ) {
  public AgentConfig {
    if (toolTimeout <= 0) {
      toolTimeout = 120_000;
    }
    if (observationCompressionThreshold <= 0) {
      observationCompressionThreshold = 6000; // default: 6 000 chars (~1 500 tokens)
    }
    if (systemOverheadTokens <= 0) {
      systemOverheadTokens =
          6000; // default: system prompt ~2000 + tool defs ~3000 + protocol ~1000
    }
    if (forceCompressionMultiplier <= 0) {
      forceCompressionMultiplier = 3.0;
    }
    if (hardLimitMultiplier <= 0) {
      hardLimitMultiplier = 4.0;
    }
  }
}
