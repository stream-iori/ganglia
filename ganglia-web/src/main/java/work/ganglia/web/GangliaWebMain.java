package work.ganglia.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;

import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.config.model.GangliaConfig;

/** Main entry point for the Ganglia Web UI server. */
public class GangliaWebMain {
  private static final Logger logger = LoggerFactory.getLogger(GangliaWebMain.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    String configPath = System.getProperty("ganglia.config", ".ganglia/config.json");
    String projectRoot = System.getProperty("user.dir");

    BootstrapOptions options =
        BootstrapOptions.builder()
            .configPath(configPath)
            .extraObservers(List.of(new WebUIEventPublisher(vertx)))
            .build();

    CodingAgentBuilder.bootstrap(vertx, options)
        .onSuccess(
            ganglia -> {
              Runtime.getRuntime().addShutdownHook(new Thread(ganglia::shutdown));

              GangliaConfig.WebUIConfig webUiConfig =
                  ganglia.configManager().getGangliaConfig().webui();
              if (webUiConfig != null && webUiConfig.enabled()) {
                int port = webUiConfig.port();
                String webroot = webUiConfig.webroot();

                WebUIVerticle webUiVerticle =
                    new WebUIVerticle(
                        port,
                        webroot,
                        ganglia.agentLoop(),
                        ganglia.sessionManager(),
                        ganglia.mcpServersCount());

                vertx
                    .deployVerticle(webUiVerticle)
                    .onSuccess(
                        id -> logger.info("Ganglia WebUI deployed successfully on port {}", port))
                    .onFailure(
                        err -> {
                          logger.error("Failed to deploy Ganglia WebUI", err);
                          System.exit(1);
                        });
              } else {
                logger.warn("WebUI is disabled in configuration.");
              }
            })
        .onFailure(
            err -> {
              logger.error("Failed to bootstrap Ganglia Core", err);
              System.exit(1);
            });
  }
}
