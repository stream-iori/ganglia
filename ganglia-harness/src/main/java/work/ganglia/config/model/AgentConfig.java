package work.ganglia.config.model;

/** General agent/loop parameters. */
public record AgentConfig(
    int maxIterations,
    double compressionThreshold, // Default 0.7 (70%)
    String projectRoot, // Root directory for file system tools
    String instructionFile // Default GANGLIA.md
    ) {}
