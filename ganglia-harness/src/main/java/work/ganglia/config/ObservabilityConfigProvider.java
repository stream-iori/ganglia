package work.ganglia.config;

/** Interface Segregation: Provides observability and tracing configuration. */
public interface ObservabilityConfigProvider {
  boolean isObservabilityEnabled();

  String getTracePath();
}
