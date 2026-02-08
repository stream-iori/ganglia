package me.stream.example;

import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.ui.TerminalUI;

import java.util.UUID;

public class GangliaExample {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        // 1. Use Main to bootstrap core logic
        Ganglia ganglia = Main.bootstrap(vertx);

        if (ganglia == null) {
            vertx.close();
            return;
        }

        // 2. Setup UI
        TerminalUI ui = new TerminalUI(vertx);

        String sessionId = UUID.randomUUID().toString();
        
        // 3. Use SessionManager to create/load context
        ganglia.sessionManager().getSession(sessionId)
            .onSuccess(context -> {
                System.out.println("--- Ganglia CLI (Streaming Enabled) ---");
                System.out.println("Session ID: " + sessionId);

                String input = args.length > 0 ? String.join(" ", args) : "Hello, how can you help me today?";
                System.out.println("\nUser: " + input);
                System.out.print("Agent: ");

                // 4. Run with Streaming
                ui.listenToStream(sessionId);

                ganglia.agentLoop().run(input, context)
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            System.out.println("\n\n[Done]");
                        } else {
                            System.err.println("\n\nError: " + ar.cause().getMessage());
                        }
                        vertx.close();
                    });
            })
            .onFailure(err -> {
                System.err.println("Failed to initialize session: " + err.getMessage());
                vertx.close();
            });
    }
}