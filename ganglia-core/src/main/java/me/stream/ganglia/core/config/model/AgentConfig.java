package me.stream.ganglia.core.config.model;

/**
 * General agent/loop parameters.
 */
public record AgentConfig(
    int maxIterations,
    double compressionThreshold, // Default 0.7 (70%)
    String projectRoot           // Root directory for file system tools
) {}
