package me.stream.ganglia.core.config.model;

import java.util.Map;

/**
 * Root configuration object for Ganglia.
 */
public record GangliaConfig(
    AgentConfig agent,
    Map<String, ModelConfig> models,
    ObservabilityConfig observability
) {
    public ModelConfig getModel(String key) {
        return models != null ? models.get(key) : null;
    }
}
