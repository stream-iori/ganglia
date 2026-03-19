package work.ganglia.example;

import io.vertx.core.Vertx;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.ui.TerminalApp;

/**
 * An interactive chat demo using JLine 3 with box-drawing responses,
 * tool cards, status bar, and markdown rendering.
 */
public class InteractiveChatDemo {

    public static void main(String[] args) {
        // Suppress console logging before any logger initialization
        TerminalApp.suppressConsoleLogging();

        Vertx vertx = Vertx.vertx();
        String projectRoot = System.getProperty("user.dir");

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot);

        CodingAgentBuilder.bootstrap(vertx, options)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                new Thread(() -> {
                    try (TerminalApp app = TerminalApp.create(vertx, ganglia)) {
                        app.run();
                    } catch (Exception e) {
                        System.err.println("Terminal error: " + e.getMessage());
                    } finally {
                        vertx.close();
                        System.exit(0);
                    }
                }, "terminal-repl").start();
            });
    }
}
