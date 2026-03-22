package work.ganglia.example;

import io.vertx.core.Vertx;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.Ganglia;
import work.ganglia.ui.TerminalUi;

/**
 * A demo specifically for verifying Google Gemini integration. It ensures the provider is set to
 * 'gemini' and runs a multi-step task to verify tool execution and streaming reasoning.
 */
public class GeminiDemo {
  private static final Logger logger = LoggerFactory.getLogger(GeminiDemo.class);

  public static void main(String[] args) {
    Ganglia.bootstrap()
        .onFailure(
            err -> {
              System.err.println("Bootstrap failed: " + err.getMessage());
            })
        .onSuccess(
            ganglia -> {
              Vertx vertx = ganglia.vertx();
              TerminalUi ui = TerminalUi.create(vertx);
              String sessionId = "gemini-demo-" + UUID.randomUUID().toString().substring(0, 8);

              System.out.println("--- Ganglia Gemini Integration Demo ---");
              System.out.println("Session ID: " + sessionId);
              System.out.println(
                  "Note: Ensure GOOGLE_API_KEY is set and provider='gemini' in .ganglia/config.json");

              ui.listenToStream(sessionId);

              String input =
                  "Hello Gemini! Can you tell me what the current date is, "
                      + "and then create a file named 'gemini_test.md' with a short story about an AI named Ganglia?";

              System.out.println("\nUser: " + input);
              System.out.println("\n--- Agent Reasoning (Gemini) ---");
              System.out.print("Agent: ");

              ganglia
                  .sessionManager()
                  .getSession(sessionId)
                  .compose(context -> ganglia.agentLoop().run(input, context))
                  .onComplete(
                      ar -> {
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
