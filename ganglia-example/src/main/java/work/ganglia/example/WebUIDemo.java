package work.ganglia.example;

import io.vertx.core.Vertx;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.config.model.GangliaConfig;
import work.ganglia.web.WebUIEventPublisher;
import work.ganglia.web.WebUIVerticle;

/**
 * Example demonstrating how to bootstrap Ganglia with the WebUI. This demo uses the new
 * 'ganglia-web' module for decoupling and CodingAgent for capabilities.
 */
public class WebUIDemo {
  private static final Logger logger = LoggerFactory.getLogger(WebUIDemo.class);

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    // 1. Setup Bootstrap Options with WebUI Observer
    BootstrapOptions options =
        BootstrapOptions.defaultOptions().withObservers(List.of(new WebUIEventPublisher(vertx)));

    // 2. Bootstrap Ganglia with Coding Capabilities
    CodingAgentBuilder.bootstrap(vertx, options)
        .onSuccess(
            ganglia -> {
              Runtime.getRuntime().addShutdownHook(new Thread(ganglia::shutdown));

              System.out.println("Ganglia Core bootstrapped successfully.");

              // 3. Extract WebUI config and deploy the Verticle from ganglia-web module
              GangliaConfig.WebUIConfig webConfig =
                  ganglia.configManager().getGangliaConfig().webui();
              if (webConfig != null && webConfig.enabled()) {
                WebUIVerticle webUiVerticle =
                    new WebUIVerticle(
                        webConfig.port(),
                        webConfig.webroot(),
                        ganglia.agentLoop(),
                        ganglia.sessionManager(),
                        ganglia.mcpServersCount());

                vertx
                    .deployVerticle(webUiVerticle)
                    .onSuccess(
                        id -> {
                          System.out.println(
                              "\n==================================================");
                          System.out.println("🚀 Ganglia WebUI is running!");
                          System.out.println("👉 URL: http://localhost:" + webConfig.port());
                          System.out.println(
                              "==================================================\n");
                        })
                    .onFailure(err -> logger.error("Failed to deploy WebUI Verticle", err));
              } else {
                logger.warn("WebUI is disabled in config. Ensure 'webui.enabled' is true.");
              }
            })
        .onFailure(
            err -> {
              logger.error("Bootstrap failed", err);
              System.exit(1);
            });
  }
}
