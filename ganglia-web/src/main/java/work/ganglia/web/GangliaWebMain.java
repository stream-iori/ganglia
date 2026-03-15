package work.ganglia.web;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.config.model.GangliaConfig;

import java.util.List;

/**
 * Main entry point for the Ganglia Web UI server.
 */
public class GangliaWebMain {
    private static final Logger logger = LoggerFactory.getLogger(GangliaWebMain.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        String configPath = System.getProperty("ganglia.config", ".ganglia/config.json");
        String projectRoot = System.getProperty("user.dir");

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withConfigPath(configPath)
            .withObservers(List.of(new WebUIEventPublisher(vertx)));

        CodingAgentBuilder.bootstrap(vertx, options)
            .onSuccess(ganglia -> {
                GangliaConfig.WebUIConfig webUIConfig = ganglia.configManager().getGangliaConfig().webui();
                if (webUIConfig != null && webUIConfig.enabled()) {
                    int port = webUIConfig.port();
                    String webroot = webUIConfig.webroot();
                    
                    WebUIVerticle webUIVerticle = new WebUIVerticle(
                        port, 
                        webroot, 
                        ganglia.agentLoop(), 
                        ganglia.sessionManager()
                    );
                    
                    vertx.deployVerticle(webUIVerticle)
                        .onSuccess(id -> logger.info("Ganglia WebUI deployed successfully on port {}", port))
                        .onFailure(err -> {
                            logger.error("Failed to deploy Ganglia WebUI", err);
                            System.exit(1);
                        });
                } else {
                    logger.warn("WebUI is disabled in configuration.");
                }
            })
            .onFailure(err -> {
                logger.error("Failed to bootstrap Ganglia Core", err);
                System.exit(1);
            });
    }
}
