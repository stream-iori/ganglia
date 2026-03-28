package work.ganglia.config.model;

/** Settings for tracing and logging. */
public record ObservabilityConfig(
    boolean enabled, String tracePath, boolean webUIEnabled, int port) {
  public ObservabilityConfig {
    if (port == 0) {
      port = 8081;
    }
  }
}
