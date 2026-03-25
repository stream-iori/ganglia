package work.ganglia.config.model;

/** General agent/loop parameters. */
public record AgentConfig(
    int maxIterations,
    double compressionThreshold, // Default 0.7 (70%)
    String projectRoot, // Root directory for file system tools
    String instructionFile, // Default GANGLIA.md
    long toolTimeout // Max tool execution time in ms (default 120000)
    ) {
  public AgentConfig {
    if (toolTimeout <= 0) {
      toolTimeout = 120_000;
    }
  }
}
