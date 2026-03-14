package work.ganglia.config;

import work.ganglia.config.model.ModelConfig;

/**
 * Interface Segregation: Provides LLM and model-specific configuration.
 */
public interface ModelConfigProvider {
    ModelConfig getModelConfig(String modelKey);
    String getModel();
    String getUtilityModel();
    double getTemperature();
    int getMaxTokens();
    int getContextLimit();
    boolean isStream();
    boolean isUtilityStream();
    String getBaseUrl();
    String getProvider();
}
