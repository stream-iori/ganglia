package me.stream.ganglia.example;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for Demo applications.
 */
public class DemoUtil {
    private static final Logger logger = LoggerFactory.getLogger(DemoUtil.class);

    /**
     * Gracefully shuts down the demo application.
     * Gives background tasks a chance to finish before closing Vert.x and exiting the JVM.
     *
     * @param vertx The Vert.x instance to close.
     */
    public static void gracefulShutdown(Vertx vertx) {
        System.out.println("
Cleaning up background tasks...");
        // Delay to allow EventBus messages (like reflection) to be processed
        vertx.setTimer(2000, id -> {
            vertx.close(ar -> {
                if (ar.succeeded()) {
                    System.out.println("Ganglia shutdown successfully.");
                } else {
                    System.err.println("Error during shutdown: " + ar.cause().getMessage());
                }
                System.exit(0);
            });
        });
    }
}
