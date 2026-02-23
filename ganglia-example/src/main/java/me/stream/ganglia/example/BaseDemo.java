package me.stream.ganglia.example;

import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.ui.TerminalUI;

import java.util.UUID;

/**
 * Base demo showing how to bootstrap and run a Ganglia agent with streaming.
 */
public class BaseDemo {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        // 1. Bootstrap core logic using Main
        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                // 2. Setup UI for streaming feedback
                TerminalUI ui = new TerminalUI(vertx);

                String sessionId = UUID.randomUUID().toString();

                // 3. Initialize or load session context
                ganglia.sessionManager().getSession(sessionId)
                    .onSuccess(context -> {
                        System.out.println("--- Ganglia CLI Base Demo ---");
                        System.out.println("Session ID: " + sessionId);

                        String input = args.length > 0 ? String.join(" ", args) : "Help me understand the current project structure.";
                        System.out.println("\nUser: " + input);
                        System.out.print("Agent: ");

                        // 4. Start listening to the streaming output
                        ui.listenToStream(sessionId);

                        // 5. Run the agent loop
                        ganglia.agentLoop().run(input, context)
                            .onComplete(ar -> {
                                if (ar.succeeded()) {
                                    System.out.println("\n\n[Turn Complete]");
                                } else {
                                    System.err.println("\n\nError: " + ar.cause().getMessage());
                                }
                                DemoUtil.gracefulShutdown(vertx);
                            });
                    })
                    .onFailure(err -> {
                        System.err.println("Failed to initialize session: " + err.getMessage());
                        DemoUtil.gracefulShutdown(vertx);
                    });
            });
    }
}
