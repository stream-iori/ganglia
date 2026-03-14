package work.ganglia.config;

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
import work.ganglia.util.Constants;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SRP: Manages configuration retrieval, watching, and multi-domain provider implementation.
 */
public class ConfigManager implements ModelConfigProvider, AgentConfigProvider, WebUIConfigProvider, ObservabilityConfigProvider {
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
                .setScanPeriod(2000);

        this.retriever = ConfigRetriever.create(vertx, options);
        this.currentJson = DefaultConfigFactory.create();

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
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(path);
            if (vertx.fileSystem().existsBlocking(candidate.toString())) {
                return candidate.toString();
            }
            current = current.getParent();
        }
        return path;
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
        return work.ganglia.util.FileSystemUtil.ensureFileWithDefault(vertx, this.configPath, DefaultConfigFactory.create().toBuffer())
                .onFailure(err -> {
                    logger.error("Critical error: Unable to create configuration file at {}. Reason: {}", this.configPath, err.getMessage());
                });
    }

    public synchronized void updateConfig(JsonObject newConfig) {
        if (this.currentJson == null) {
            this.currentJson = DefaultConfigFactory.create();
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

    // --- ModelConfigProvider Implementation ---

    @Override
    public ModelConfig getModelConfig(String modelKey) {
        return currentConfig.getModel(modelKey);
    }

    @Override
    public String getModel() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.name() : "gpt-4o";
    }

    @Override
    public String getUtilityModel() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.UTILITY);
        return mc != null ? mc.name() : "gpt-4o-mini";
    }

    @Override
    public double getTemperature() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.temperature() : 0.0;
    }

    @Override
    public int getMaxTokens() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.maxTokens() : 4096;
    }

    @Override
    public int getContextLimit() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.contextLimit() : 128000;
    }

    @Override
    public boolean isStream() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null && mc.stream() != null ? mc.stream() : true;
    }

    @Override
    public boolean isUtilityStream() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.UTILITY);
        return mc != null && mc.stream() != null ? mc.stream() : false;
    }

    @Override
    public String getBaseUrl() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.baseUrl() : "https://api.openai.com/v1";
    }

    @Override
    public String getProvider() {
        ModelConfig mc = currentConfig.getModel(ConfigKeys.PRIMARY);
        return mc != null ? mc.type() : "openai";
    }

    // --- AgentConfigProvider Implementation ---

    @Override
    public int getMaxIterations() {
        return currentConfig.agent() != null ? currentConfig.agent().maxIterations() : 10;
    }

    @Override
    public double getCompressionThreshold() {
        return currentConfig.agent() != null ? currentConfig.agent().compressionThreshold() : 0.7;
    }

    @Override
    public String getProjectRoot() {
        return (currentConfig.agent() != null && currentConfig.agent().projectRoot() != null)
            ? currentConfig.agent().projectRoot()
            : System.getProperty("user.dir");
    }

    // --- WebUIConfigProvider Implementation ---

    @Override
    public boolean isWebUIEnabled() {
        return currentConfig.webui() != null && currentConfig.webui().enabled();
    }

    @Override
    public int getWebUIPort() {
        return currentConfig.webui() != null ? currentConfig.webui().port() : 8080;
    }

    @Override
    public String getWebRoot() {
        return currentConfig.webui() != null ? currentConfig.webui().webroot() : "webroot";
    }

    // --- ObservabilityConfigProvider Implementation ---

    @Override
    public boolean isObservabilityEnabled() {
        return currentConfig.observability() != null && currentConfig.observability().enabled();
    }

    @Override
    public String getTracePath() {
        return (currentConfig.observability() != null) ? currentConfig.observability().tracePath() : Constants.DIR_TRACE;
    }
}
