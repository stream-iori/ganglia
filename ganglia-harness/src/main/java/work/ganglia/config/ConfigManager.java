package work.ganglia.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import work.ganglia.config.model.GangliaConfig;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.util.Constants;

/**
 * SRP: Acts as the central registry for configuration state. Implements multi-domain provider
 * interfaces and handles JSON deep-merging.
 */
public class ConfigManager
    implements ModelConfigProvider,
        AgentConfigProvider,
        WebUIConfigProvider,
        ObservabilityConfigProvider {
  private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
  private static final String DEFAULT_CONFIG_FILE = Constants.DEFAULT_CONFIG_FILE;

  private final ConfigLoader loader;
  private JsonObject currentJson;
  private GangliaConfig currentConfig;
  private final List<Consumer<GangliaConfig>> listeners = new ArrayList<>();

  public ConfigManager(Vertx vertx) {
    this(vertx, DEFAULT_CONFIG_FILE);
  }

  public ConfigManager(Vertx vertx, String configPath) {
    this.loader = new ConfigLoader(vertx, configPath);
    this.currentJson = DefaultConfigFactory.create();

    // Initial load (sync/blocking part for immediate access)
    JsonObject fileConfig = loader.readInitialBlocking();
    if (!fileConfig.isEmpty()) {
      this.currentJson.mergeIn(fileConfig, true);
      logger.debug("Initial configuration loaded from {}", loader.getConfigPath());
    }
    this.currentConfig = this.currentJson.mapTo(GangliaConfig.class);
  }

  /**
   * Initializes the config manager, delegating to the loader to start watching and fetch the full
   * config.
   *
   * @return A future that completes when the initial configuration is fully loaded.
   */
  public Future<Void> init() {
    return loader
        .load()
        .map(
            config -> {
              updateConfig(config);
              loader.listen(
                  newConfig -> {
                    logger.info("Configuration changed, updating...");
                    updateConfig(newConfig);
                  });
              return null;
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

  public String getConfigPath() {
    return loader.getConfigPath();
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
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::name, "gpt-4o");
  }

  @Override
  public String getUtilityModel() {
    String utility = getModelProp(ConfigKeys.UTILITY, ModelConfig::name, null);
    return utility != null ? utility : getModel();
  }

  @Override
  public double getTemperature() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::temperature, 0.0);
  }

  @Override
  public int getMaxTokens() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::maxTokens, 4096);
  }

  @Override
  public int getContextLimit() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::contextLimit, 128000);
  }

  @Override
  public boolean isStream() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::stream, true);
  }

  @Override
  public boolean isUtilityStream() {
    return getModelProp(ConfigKeys.UTILITY, ModelConfig::stream, false);
  }

  @Override
  public String getBaseUrl() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::baseUrl, "https://api.openai.com/v1");
  }

  @Override
  public String getProvider() {
    return getModelProp(ConfigKeys.PRIMARY, ModelConfig::type, "openai");
  }

  private <T> T getModelProp(String modelKey, Function<ModelConfig, T> getter, T defaultValue) {
    return Optional.ofNullable(currentConfig.getModel(modelKey)).map(getter).orElse(defaultValue);
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

  @Override
  public String getInstructionFile() {
    return (currentConfig.agent() != null && currentConfig.agent().instructionFile() != null)
        ? currentConfig.agent().instructionFile()
        : Constants.FILE_GANGLIA_MD;
  }

  @Override
  public long getToolTimeoutMs() {
    return (currentConfig.agent() != null && currentConfig.agent().toolTimeout() > 0)
        ? currentConfig.agent().toolTimeout()
        : 120_000;
  }

  // --- WebUiConfigProvider Implementation ---

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
    return (currentConfig.observability() != null)
        ? currentConfig.observability().tracePath()
        : Constants.DIR_TRACE;
  }
}
