package work.ganglia.config;

/** Interface Segregation: Provides Agent execution and project configuration. */
public interface AgentConfigProvider {
  int getMaxIterations();

  double getCompressionThreshold();

  String getProjectRoot();

  String getInstructionFile();

  /** Maximum time in milliseconds a single tool execution may run before being timed out. */
  default long getToolTimeoutMs() {
    return 120_000;
  }
}
