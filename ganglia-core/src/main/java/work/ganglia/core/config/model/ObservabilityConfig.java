package work.ganglia.core.config.model;

/**
 * Settings for tracing and logging.
 */
public record ObservabilityConfig(
    boolean enabled,
    String tracePath
) {}
