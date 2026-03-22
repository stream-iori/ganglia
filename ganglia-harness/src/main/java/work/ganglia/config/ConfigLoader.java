package work.ganglia.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SRP: Responsible for resolving the configuration file path, initializing the Vert.x
 * ConfigRetriever, and ensuring the config file exists with default values.
 */
public class ConfigLoader {
  private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);

  private final Vertx vertx;
  private final String configPath;
  private final ConfigRetriever retriever;

  public ConfigLoader(Vertx vertx, String path) {
    this.vertx = vertx;
    this.configPath = resolveConfigPath(vertx, path);

    ConfigStoreOptions fileStore =
        new ConfigStoreOptions()
            .setType("file")
            .setOptional(true)
            .setConfig(new JsonObject().put("path", this.configPath));

    ConfigRetrieverOptions options =
        new ConfigRetrieverOptions().addStore(fileStore).setScanPeriod(2000);

    this.retriever = ConfigRetriever.create(vertx, options);
  }

  public String getConfigPath() {
    return configPath;
  }

  /**
   * Initializes the config loader, ensuring the file exists and starting the retriever.
   *
   * @return A future that completes with the initial configuration.
   */
  public Future<JsonObject> load() {
    return ensureConfigExists().compose(v -> retriever.getConfig());
  }

  /**
   * Registers a listener for configuration changes.
   *
   * @param listener The callback to be invoked when configuration changes.
   */
  public void listen(io.vertx.core.Handler<JsonObject> listener) {
    retriever.listen(change -> listener.handle(change.getNewConfiguration()));
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

  private Future<Void> ensureConfigExists() {
    return work.ganglia.util.FileSystemUtil.ensureFileWithDefault(
            vertx, this.configPath, DefaultConfigFactory.create().toBuffer())
        .onFailure(
            err -> {
              logger.error(
                  "Critical error: Unable to create configuration file at {}. Reason: {}",
                  this.configPath,
                  err.getMessage());
            });
  }

  /**
   * Manually reads the config file directly without using ConfigRetriever (useful for immediate
   * bootstrapping).
   */
  public JsonObject readInitialBlocking() {
    try {
      if (vertx.fileSystem().existsBlocking(this.configPath)) {
        return vertx.fileSystem().readFileBlocking(this.configPath).toJsonObject();
      }
    } catch (Exception e) {
      logger.warn(
          "No initial configuration file found or failed to load at {}. Using defaults.",
          this.configPath);
    }
    return new JsonObject();
  }
}
