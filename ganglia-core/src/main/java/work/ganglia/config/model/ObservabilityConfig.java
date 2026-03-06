package work.ganglia.config.model;

/**
 * Settings for tracing and logging.
 */
public record ObservabilityConfig(
    boolean enabled,
    String tracePath
) {}
