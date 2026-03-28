package work.ganglia.config;

import work.ganglia.config.model.ModelConfig;

/** Interface Segregation: Provides LLM and model-specific configuration. */
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

  /**
   * Character-length threshold above which {@code LLMObservationCompressor} triggers LLM-based
   * compression for irreproducible tool outputs. Valid range: 5 000–10 000. Default: 6 000.
   */
  default int getObservationCompressionThreshold() {
    return 6000;
  }
}
