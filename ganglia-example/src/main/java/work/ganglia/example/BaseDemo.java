package work.ganglia.example;

import io.vertx.core.Vertx;
import java.util.UUID;
import work.ganglia.Ganglia;
import work.ganglia.ui.TerminalUi;

/** Base demo showing how to bootstrap and run a Ganglia agent with streaming. */
public class BaseDemo {

  public static void main(String[] args) {
    // 1. Bootstrap core logic using Ganglia (zero-args uses a default Vertx instance)
    Ganglia.bootstrap()
        .onFailure(
            err -> {
              System.err.println("Bootstrap failed: " + err.getMessage());
            })
        .onSuccess(
            ganglia -> {
              Vertx vertx = ganglia.vertx();
              // 2. Setup UI for streaming feedback
              TerminalUi ui = TerminalUi.create(vertx);

              String sessionId = UUID.randomUUID().toString();

              // 3. Initialize or load session context
              ganglia
                  .sessionManager()
                  .getSession(sessionId)
                  .onSuccess(
                      context -> {
                        System.out.println("--- Ganglia CLI Base Demo ---");
                        System.out.println("Session ID: " + sessionId);

                        String input =
                            args.length > 0
                                ? String.join(" ", args)
                                : "Help me understand the current project structure.";
                        System.out.println("\nUser: " + input);
                        System.out.print("Agent: ");

                        // 4. Start listening to the streaming output
                        ui.listenToStream(sessionId);

                        // 5. Run the agent loop
                        ganglia
                            .agentLoop()
                            .run(input, context)
                            .onComplete(
                                ar -> {
                                  if (ar.succeeded()) {
                                    System.out.println("\n\n[Turn Complete]");
                                  } else {
                                    System.err.println("\n\nError: " + ar.cause().getMessage());
                                  }
                                  DemoUtil.gracefulShutdown(vertx);
                                });
                      })
                  .onFailure(
                      err -> {
                        System.err.println("Failed to initialize session: " + err.getMessage());
                        DemoUtil.gracefulShutdown(vertx);
                      });
            });
  }
}
