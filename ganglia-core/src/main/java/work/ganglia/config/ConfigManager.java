package work.ganglia.config;

import work.ganglia.util.Constants;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.config.model.GangliaConfig;
import work.ganglia.config.model.ModelConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String DEFAULT_CONFIG_FILE = Constants.DEFAULT_CONFIG_FILE;

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
        this.configPath = resolveConfigPath(vertx, configPath);

        ConfigStoreOptions fileStore = new ConfigStoreOptions()
                .setType("file")
                .setOptional(true)
                .setConfig(new JsonObject().put("path", this.configPath));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(fileStore)
                .setScanPeriod(2000); // Check for changes every 2 seconds

        this.retriever = ConfigRetriever.create(vertx, options);

        // Initial setup with defaults
        this.currentJson = getDefaultConfig();

        // Load initial file if exists
        try {
            if (vertx.fileSystem().existsBlocking(this.configPath)) {
                JsonObject fileConfig = vertx.fileSystem().readFileBlocking(this.configPath).toJsonObject();
                this.currentJson.mergeIn(fileConfig, true);
                logger.debug("Initial configuration loaded from {}", this.configPath);
            }
        } catch (Exception e) {
            logger.warn("No initial configuration file found or failed to load at {}. Using defaults.", this.configPath);
        }
        this.currentConfig = this.currentJson.mapTo(GangliaConfig.class);
    }

    private String resolveConfigPath(Vertx vertx, String path) {
        if (vertx.fileSystem().existsBlocking(path)) {
            return path;
        }

        // Search upwards for .ganglia/config.json or the specified path
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (vertx.fileSystem().existsBlocking(candidate.toString())) {
                logger.debug("Found config file at: {}", candidate);
                return candidate.toString();
            }
            current = current.getParent();
        }

        return path; // Fallback to original
    }

    public Future<Void> init() {
        return ensureConfigExists()
                .compose(v -> retriever.getConfig())
                .map(config -> {
                    updateConfig(config);

                    retriever.listen(change -> {
                        logger.info("Configuration changed, updating...");
                        updateConfig(change.getNewConfiguration());
                    });
                    return null;
                });
    }

    private Future<Void> ensureConfigExists() {
        return vertx.fileSystem().exists(this.configPath)
                .compose(exists -> {
                    if (exists) {
                        return Future.succeededFuture();
                    }

                    logger.info("No configuration file found at {}. Initializing new configuration with defaults.", this.configPath);

                    Path path = Paths.get(this.configPath);
                    Path parent = path.getParent();

                    Future<Void> dirFuture = parent != null ? vertx.fileSystem().mkdirs(parent.toString()) : Future.succeededFuture();

                    return dirFuture.compose(v -> {
                        JsonObject defaultConfig = getDefaultConfig();
                        return vertx.fileSystem().writeFile(this.configPath, defaultConfig.toBuffer());
                    }).onFailure(err -> {
                        logger.error("Critical error: Unable to create configuration file at {}. Reason: {}", this.configPath, err.getMessage());
                    });
                });
    }

    public synchronized void updateConfig(JsonObject newConfig) {
        // Merge with current state or defaults
        if (this.currentJson == null) {
            this.currentJson = getDefaultConfig();
        }
        deepMerge(this.currentJson, newConfig);

        this.currentConfig = this.currentJson.mapTo(GangliaConfig.class);
        listeners.forEach(l -> l.accept(this.currentConfig));
    }

    private void deepMerge(JsonObject base, JsonObject overlay) {
        for (String key : overlay.fieldNames()) {
            Object value = overlay.getValue(key);
            if (value instanceof JsonObject && base.getValue(key) instanceof JsonObject) {
                deepMerge(base.getJsonObject(key), (JsonObject) value);
            } else {
                base.put(key, value);
            }
        }
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
                .put(ConfigKeys.NAME, "kimi-k2-0905-preview")
                .put(ConfigKeys.TEMPERATURE, 0.0)
                .put(ConfigKeys.MAX_TOKENS, 4096)
                .put(ConfigKeys.CONTEXT_LIMIT, 128000)
                .put(ConfigKeys.TYPE, "openai")
                .put(ConfigKeys.API_KEY, "")
                .put(ConfigKeys.BASE_URL, "https://api.moonshot.cn/v1");

        JsonObject utilityModel = new JsonObject()
                .put(ConfigKeys.NAME, "moonshot-v1-8k")
                .put(ConfigKeys.TEMPERATURE, 0.0)
                .put(ConfigKeys.MAX_TOKENS, 2048)
                .put(ConfigKeys.CONTEXT_LIMIT, 128000)
                .put(ConfigKeys.TYPE, "openai")
                .put(ConfigKeys.API_KEY, "")
                .put(ConfigKeys.BASE_URL, "https://api.moonshot.cn/v1");

        Map<String, Object> models = new HashMap<>();
        models.put(ConfigKeys.PRIMARY, primaryModel);
        models.put(ConfigKeys.UTILITY, utilityModel);

        return new JsonObject()
                .put(ConfigKeys.AGENT, new JsonObject()
                        .put(ConfigKeys.MAX_ITERATIONS, 10)
                        .put(ConfigKeys.COMPRESSION_THRESHOLD, 0.7))
                .put(ConfigKeys.MODELS, models)
                .put(ConfigKeys.OBSERVABILITY, new JsonObject()
                        .put(ConfigKeys.ENABLED, false)
                        .put(ConfigKeys.TRACE_PATH, Constants.DIR_TRACE))
                .put(ConfigKeys.WEBUI, new JsonObject()
                        .put(ConfigKeys.ENABLED, true)
                        .put(ConfigKeys.PORT, 8080)
                        .put(ConfigKeys.WEBROOT, "webroot"));
    }

    // --- Backward compatibility and convenience getters ---

    public String getModel() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.name() : "gpt-4o";
    }

    public String getUtilityModel() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.UTILITY);
        return mc != null ? mc.name() : "gpt-4o-mini";
    }

    public double getTemperature() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.temperature() : 0.0;
    }

    public int getMaxTokens() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.maxTokens() : 4096;
    }

    public int getContextLimit() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.contextLimit() : 128000;
    }

    public boolean isStream() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null && mc.stream() != null ? mc.stream() : true;
    }

    public boolean isUtilityStream() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.UTILITY);
        return mc != null && mc.stream() != null ? mc.stream() : false;
    }

    public double getCompressionThreshold() {
        return currentConfig.agent() != null ? currentConfig.agent().compressionThreshold() : 0.7;
    }

    public String getProjectRoot() {
        return (currentConfig.agent() != null && currentConfig.agent().projectRoot() != null)
            ? currentConfig.agent().projectRoot()
            : System.getProperty("user.dir");
    }

    public int getMaxIterations() {
        return currentConfig.agent() != null ? currentConfig.agent().maxIterations() : 10;
    }

    public String getBaseUrl() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.baseUrl() : "https://api.openai.com/v1";
    }

    public String getProvider() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.type() : "openai";
    }

    public boolean isObservabilityEnabled() {
        return currentConfig.observability() != null && currentConfig.observability().enabled();
    }

    public String getTracePath() {
        return (currentConfig.observability() != null) ? currentConfig.observability().tracePath() : Constants.DIR_TRACE;
    }
}
