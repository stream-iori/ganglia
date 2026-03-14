package work.ganglia.config;

/**
 * Interface Segregation: Provides Agent execution and project configuration.
 */
public interface AgentConfigProvider {
    int getMaxIterations();
    double getCompressionThreshold();
    String getProjectRoot();
}
