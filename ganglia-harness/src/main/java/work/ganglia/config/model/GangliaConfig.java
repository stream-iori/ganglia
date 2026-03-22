package work.ganglia.config.model;

import java.util.Map;

/** Root configuration object for Ganglia. */
public record GangliaConfig(
    AgentConfig agent,
    Map<String, ModelConfig> models,
    ObservabilityConfig observability,
    WebUiConfig webui) {
  public ModelConfig getModel(String key) {
    return models != null ? models.get(key) : null;
  }

  public record WebUiConfig(int port, boolean enabled, String webroot) {
    public WebUiConfig {
      if (port == 0) {
        port = 8080;
      }
    }
  }
}
