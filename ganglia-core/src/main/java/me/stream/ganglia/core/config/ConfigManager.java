package me.stream.ganglia.core.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.config.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = ".ganglia/config.json";

    private final Vertx vertx;
    private final ConfigRetriever retriever;
    private final String configPath;
    private JsonObject currentJson;
    private GangliaConfig currentConfig;
    private final List<Consumer<GangliaConfig>> listeners = new ArrayList<>();

    public ConfigManager(Vertx vertx) {
        this(vertx, DEFAULT_CONFIG_FILE);
    }

    public ConfigManager(Vertx vertx, String configPath) {
        this.vertx = vertx;
        this.configPath = configPath;
        
        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setOptional(true)
                .setConfig(new JsonObject().put("path", configPath));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(fileStore)
                .setScanPeriod(2000); // Check for changes every 2 seconds

        this.retriever = ConfigRetriever.create(vertx, options);
        
        // Initial setup with defaults
        this.currentJson = getDefaultConfig();
        
        // Load initial file if exists
        try {
            if (vertx.fileSystem().existsBlocking(configPath)) {
                JsonObject fileConfig = vertx.fileSystem().readFileBlocking(configPath).toJsonObject();
                this.currentJson.mergeIn(fileConfig, true);
                logger.debug("Initial configuration loaded from {}", configPath);
            }
        } catch (Exception e) {
            logger.warn("No initial configuration file found or failed to load at {}. Using defaults.", configPath);
        }
        this.currentConfig = this.currentJson.mapTo(GangliaConfig.class);
    }

    public Future<Void> init() {
        return retriever.getConfig()
                .map(config -> {
                    updateConfig(config);
                    
                    retriever.listen(change -> {
                        logger.info("Configuration changed, updating...");
                        updateConfig(change.getNewConfiguration());
                    });
                    return null;
                });
    }

    public synchronized void updateConfig(JsonObject newConfig) {
        // Merge with current state or defaults
        if (this.currentJson == null) {
            this.currentJson = getDefaultConfig();
        }
        this.currentJson.mergeIn(newConfig, true);
        
        this.currentConfig = this.currentJson.mapTo(GangliaConfig.class);
        listeners.forEach(l -> l.accept(this.currentConfig));
    }

    public synchronized GangliaConfig getGangliaConfig() {
        return currentConfig;
    }

    public synchronized JsonObject getConfig() {
        return currentJson;
    }

    public void listen(Consumer<GangliaConfig> listener) {
        listeners.add(listener);
    }

    private JsonObject getDefaultConfig() {
        // Create structured default JSON
        JsonObject primaryModel = new JsonObject()
                .put("name", "gpt-4o")
                .put("temperature", 0.0)
                .put("maxTokens", 4096)
                .put("type", "openai")
                .put("apiKey", "")
                .put("baseUrl", "https://api.openai.com/v1");

        JsonObject utilityModel = new JsonObject()
                .put("name", "gpt-4o-mini")
                .put("temperature", 0.0)
                .put("maxTokens", 2048)
                .put("type", "openai")
                .put("apiKey", "")
                .put("baseUrl", "https://api.openai.com/v1");

        Map<String, Object> models = new HashMap<>();
        models.put("primary", primaryModel);
        models.put("utility", utilityModel);

        return new JsonObject()
                .put("agent", new JsonObject().put("maxIterations", 10))
                .put("models", models)
                .put("observability", new JsonObject()
                        .put("enabled", false)
                        .put("tracePath", ".ganglia/trace"));
    }

    // --- Backward compatibility and convenience getters ---

    public String getModel() {
        ModelConfig mc = currentConfig.getModel("primary");
        return mc != null ? mc.name() : "gpt-4o";
    }

    public String getUtilityModel() {
        ModelConfig mc = currentConfig.getModel("utility");
        return mc != null ? mc.name() : "gpt-4o-mini";
    }

    public double getTemperature() {
        ModelConfig mc = currentConfig.getModel("primary");
        return mc != null ? mc.temperature() : 0.0;
    }

    public int getMaxTokens() {
        ModelConfig mc = currentConfig.getModel("primary");
        return mc != null ? mc.maxTokens() : 4096;
    }

    public int getMaxIterations() {
        return currentConfig.agent() != null ? currentConfig.agent().maxIterations() : 10;
    }

    public String getBaseUrl() {
        ModelConfig mc = currentConfig.getModel("primary");
        return mc != null ? mc.baseUrl() : "https://api.openai.com/v1";
    }

    public String getProvider() {
        ModelConfig mc = currentConfig.getModel("primary");
        return mc != null ? mc.type() : "openai";
    }

    public boolean isObservabilityEnabled() {
        return currentConfig.observability() != null && currentConfig.observability().enabled();
    }

    public String getTracePath() {
        return (currentConfig.observability() != null) ? currentConfig.observability().tracePath() : ".ganglia/trace";
    }
}
