package me.stream.ganglia.core.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = ".ganglia/config.json";

    private final Vertx vertx;
    private final ConfigRetriever retriever;
    private final String configPath;
    private JsonObject currentConfig;
    private final List<Consumer<JsonObject>> listeners = new ArrayList<>();

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
        this.currentConfig = getDefaultConfig();
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

    private synchronized void updateConfig(JsonObject newConfig) {
        // Merge with defaults to ensure all keys exist
        this.currentConfig = getDefaultConfig().mergeIn(newConfig, true);
        listeners.forEach(l -> l.accept(this.currentConfig));
    }

    public synchronized JsonObject getConfig() {
        return currentConfig.copy();
    }

    public void listen(Consumer<JsonObject> listener) {
        listeners.add(listener);
    }

    private JsonObject getDefaultConfig() {
        return new JsonObject()
                .put("model", "gpt-4o")
                .put("utilityModel", "gpt-4o-mini")
                .put("temperature", 0.0)
                .put("maxTokens", 4096)
                .put("baseUrl", "https://api.openai.com/v1");
    }

    public String getModel() {
        return getConfig().getString("model");
    }

    public String getUtilityModel() {
        return getConfig().getString("utilityModel");
    }

    public double getTemperature() {
        return getConfig().getDouble("temperature");
    }

    public int getMaxTokens() {
        return getConfig().getInteger("maxTokens");
    }

    public String getBaseUrl() {
        return getConfig().getString("baseUrl");
    }
}
