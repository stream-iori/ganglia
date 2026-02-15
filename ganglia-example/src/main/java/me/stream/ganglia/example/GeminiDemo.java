package me.stream.ganglia.example;

import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.ui.TerminalUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A demo specifically for verifying Google Gemini integration.
 * It ensures the provider is set to 'gemini' and runs a multi-step task
 * to verify tool execution and streaming reasoning.
 */
public class GeminiDemo {
    private static final Logger logger = LoggerFactory.getLogger(GeminiDemo.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = new TerminalUI(vertx);
                String sessionId = "gemini-demo-" + UUID.randomUUID().toString().substring(0, 8);
                
                System.out.println("--- Ganglia Gemini Integration Demo ---");
                System.out.println("Session ID: " + sessionId);
                System.out.println("Note: Ensure GOOGLE_API_KEY is set and provider='gemini' in .ganglia/config.json");

                ui.listenToStream(sessionId);

                String input = "Hello Gemini! Can you tell me what the current date is, " +
                               "and then create a file named 'gemini_test.md' with a short story about an AI named Ganglia?";

                System.out.println("\nUser: " + input);
                System.out.println("\n--- Agent Reasoning (Gemini) ---");
                System.out.print("Agent: ");

                ganglia.sessionManager().getSession(sessionId)
                    .compose(context -> ganglia.agentLoop().run(input, context))
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            System.out.println("\n\n--- Workflow Complete ---");
                            System.out.println("Final Agent Response: " + ar.result());
                        } else {
                            System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
                            ar.cause().printStackTrace();
                        }
                        vertx.close();
                    });
            });
    }
}
