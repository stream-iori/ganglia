package work.ganglia.example;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.Main;

/**
 * A dedicated entry point for running Ganglia with the WebUI.
 * This demo starts the backend services and EventBus bridge, then waits for connections.
 */
public class WebUIDemo {
    private static final Logger logger = LoggerFactory.getLogger(WebUIDemo.class);

    public static void main(String[] args) {
        // Force log4j2 configuration if needed
        System.setProperty("log4j.configurationFile", "log4j2.xml");
        
        Vertx vertx = Vertx.vertx();

        System.out.println("Starting Ganglia WebUI Backend...");
        
        // 1. Bootstrap core logic (search for config in project root)
        Main.bootstrap(vertx, ".ganglia/config.json")
            .onFailure(err -> {
                logger.error("Bootstrap failed", err);
                System.err.println("CRITICAL: Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                System.out.println("==================================================");
                System.out.println("🚀 Ganglia WebUI Backend is now RUNNING");
                System.out.println("==================================================");
                System.out.println("Core Port: 8080");
                System.out.println("EventBus Endpoint: http://localhost:8080/eventbus");
                System.out.println("Static Webroot: http://localhost:8080/index.html");
                System.out.println("==================================================");
                System.out.println("Press Ctrl+C to stop the server.");
                
                // The server is kept alive by the deployed WebUIVerticle
                // No interactive CLI loop here to avoid cluttering the terminal 
                // while the Agent is working via the WebUI.
            });
    }
}
