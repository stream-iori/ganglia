package work.ganglia.example;

import io.vertx.core.Vertx;
import work.ganglia.Ganglia;
import work.ganglia.ui.TerminalUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * A demo specifically for verifying Anthropic Claude integration.
 * It ensures the provider is set to 'anthropic' and runs a multi-step task
 * to verify tool execution and streaming reasoning.
 */
public class ClaudeDemo {
    private static final Logger logger = LoggerFactory.getLogger(ClaudeDemo.class);

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Ganglia.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                DemoUtil.gracefulShutdown(vertx);
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = TerminalUI.create(vertx);
                String sessionId = "claude-demo-" + UUID.randomUUID().toString().substring(0, 8);

                System.out.println("--- Ganglia Claude Integration Demo ---");
                System.out.println("Session ID: " + sessionId);
                System.out.println("Note: Ensure ANTHROPIC_API_KEY is set and provider='anthropic' in .ganglia/config.json");

                ui.listenToStream(sessionId);

                String input = "Hello Claude! Can you tell me what operating system you are running on, " +
                               "and then create a file named 'claude_test.md' with a short poem about neurons?";

                System.out.println("\nUser: " + input);
                System.out.println("\n--- Agent Reasoning (Claude) ---");
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
                        DemoUtil.gracefulShutdown(vertx);
                    });
            });
    }
}
