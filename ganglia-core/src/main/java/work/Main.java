package work;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.config.ConfigManager;
import work.ganglia.kernel.GangliaKernel;

/**
 * Bootstrap entry point for the Ganglia framework.
 */
public class Main {

    /**
     * Initializes the Ganglia framework with default configuration.
     *
     * @param vertx The Vertx instance to use.
     * @return A future that completes with the initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx) {
        return bootstrap(vertx, BootstrapOptions.defaultOptions());
    }

    /**
     * Initializes the Ganglia framework with specific options.
     *
     * @param vertx   The Vertx instance to use.
     * @param options Initialization options.
     * @return A future that completes with the initialized Ganglia instance.
     */
    public static Future<Ganglia> bootstrap(Vertx vertx, BootstrapOptions options) {
        return new GangliaKernel(vertx, options).init();
    }

    /**
     * Legacy bootstrap method for backward compatibility.
     */
    @Deprecated
    public static Future<Ganglia> bootstrap(Vertx vertx, String configPath, JsonObject overrideConfig, work.ganglia.port.external.llm.ModelGateway gateway) {
        BootstrapOptions options = BootstrapOptions.defaultOptions()
                .withConfigPath(configPath)
                .withOverrideConfig(overrideConfig)
                .withModelGateway(gateway);
        return bootstrap(vertx, options);
    }

    public static void main(String[] args) {
        System.out.println("Ganglia Core Bootstrapper. Start with ganglia-terminal or ganglia-web modules for UI.");
    }
}
