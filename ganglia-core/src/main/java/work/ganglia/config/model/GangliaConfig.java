package work.ganglia.config.model;

import java.util.Map;

/**
 * Root configuration object for Ganglia.
 */
public record GangliaConfig(
    AgentConfig agent,
    Map<String, ModelConfig> models,
    ObservabilityConfig observability,
    WebUIConfig webui
) {
    public ModelConfig getModel(String key) {
        return models != null ? models.get(key) : null;
    }

    public record WebUIConfig(
        int port,
        boolean enabled,
        String webroot
    ) {
        public WebUIConfig {
            if (port == 0) port = 8080;
        }
    }
}
