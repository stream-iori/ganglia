package work.ganglia.config;

import work.ganglia.config.model.ModelConfig;

/** Interface Segregation: Provides LLM and model-specific configuration. */
public interface ModelConfigProvider {

  // ── Default values (single source of truth for fallbacks) ─────────────
  int DEFAULT_OBSERVATION_COMPRESSION_THRESHOLD = 6000;
  int DEFAULT_UTILITY_CONTEXT_LIMIT = 32000;

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
    return DEFAULT_OBSERVATION_COMPRESSION_THRESHOLD;
  }

  /** Context window size of the utility model used for compression tasks. Default: 32 000. */
  default int getUtilityContextLimit() {
    return DEFAULT_UTILITY_CONTEXT_LIMIT;
  }
}
