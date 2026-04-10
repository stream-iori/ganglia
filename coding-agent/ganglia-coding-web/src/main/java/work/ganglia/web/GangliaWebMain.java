package work.ganglia.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Vertx;

import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.config.model.GangliaConfig;
import work.ganglia.observability.ObservabilityVerticle;
import work.ganglia.trajectory.trace.TraceManager;

/** Main entry point for the Ganglia Web UI server. */
public class GangliaWebMain {
  private static final Logger logger = LoggerFactory.getLogger(GangliaWebMain.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    String configPath = System.getProperty("ganglia.config", ".ganglia/config.json");

    BootstrapOptions options =
        BootstrapOptions.builder()
            .configPath(configPath)
            .extraObservers(List.of(new WebUIEventPublisher(vertx)))
            .traceWriterFactory(TraceManager::new)
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
                String tracePath =
                    ganglia.configManager().getGangliaConfig().observability() != null
                        ? ganglia.configManager().getGangliaConfig().observability().tracePath()
                        : ".ganglia/trace";

                WebUIVerticle webUiVerticle =
                    new WebUIVerticle(
                        port,
                        webroot,
                        tracePath,
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

              // Deploy Observability Studio
              work.ganglia.config.model.ObservabilityConfig obsConfig =
                  ganglia.configManager().getGangliaConfig().observability();
              if (obsConfig != null && obsConfig.webUIEnabled()) {
                int port = obsConfig.port();
                String webroot =
                    ganglia.configManager().getGangliaConfig().webui() != null
                        ? ganglia.configManager().getGangliaConfig().webui().webroot()
                        : "webroot";

                String tracePath = obsConfig.tracePath();

                ObservabilityVerticle obsVerticle =
                    new ObservabilityVerticle(port, webroot, tracePath);
                vertx
                    .deployVerticle(obsVerticle)
                    .onSuccess(id -> logger.info("Observability Studio deployed on port {}", port))
                    .onFailure(err -> logger.error("Failed to deploy Observability Studio", err));
              }
            })
        .onFailure(
            err -> {
              logger.error("Failed to bootstrap Ganglia Core", err);
              System.exit(1);
            });
  }
}
