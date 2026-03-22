package work.ganglia.example;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import java.util.UUID;
import work.ganglia.Ganglia;
import work.ganglia.ui.TerminalUi;

/**
 * A demo showcasing Parent-Child Agent cooperation. The Parent Agent delegates a messy
 * investigation task to a Sub-Agent (Investigator), then uses the high-level report to perform a
 * calculation.
 */
public class SubAgentCooperationDemo {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    // 1. Setup sample data files in a 'temp_data' directory
    String dataDir = "temp_subagent_data";
    vertx.fileSystem().mkdirsBlocking(dataDir);
    vertx.fileSystem().writeFileBlocking(dataDir + "/sales_q1.txt", Buffer.buffer("Revenue: 1500"));
    vertx.fileSystem().writeFileBlocking(dataDir + "/sales_q2.txt", Buffer.buffer("Revenue: 2300"));
    vertx.fileSystem().writeFileBlocking(dataDir + "/sales_q3.txt", Buffer.buffer("Revenue: 1200"));

    Ganglia.bootstrap(vertx)
        .onFailure(
            err -> {
              System.err.println("Bootstrap failed: " + err.getMessage());
              vertx.close();
            })
        .onSuccess(
            ganglia -> {
              TerminalUi ui = TerminalUi.create(vertx);
              String sessionId = "parent-session-" + UUID.randomUUID().toString().substring(0, 8);

              System.out.println("--- Ganglia Sub-Agent Cooperation Demo ---");
              System.out.println(
                  "Goal: Sum revenues from all files in '" + dataDir + "' using a Sub-Agent.");

              ui.listenToStream(sessionId);

              // We give a complex instruction that encourages delegation
              String input =
                  String.format(
                      "Please spawn a specialized INVESTIGATOR to read all files in '%s'. "
                          + "Have them extract the revenue numbers and give me a report. "
                          + "Then, you (the parent) calculate the total annual revenue based on that report.",
                      dataDir);

              System.out.println("\nUser: " + input);
              System.out.println("\n--- Orchestrator Reasoning Start ---");
              System.out.print("Agent: ");

              ganglia
                  .sessionManager()
                  .getSession(sessionId)
                  .compose(context -> ganglia.agentLoop().run(input, context))
                  .onComplete(
                      ar -> {
                        if (ar.succeeded()) {
                          System.out.println("\n\n--- Final Result ---");
                          System.out.println(ar.result());
                        } else {
                          System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
                        }

                        // Cleanup data directory and shutdown
                        vertx
                            .fileSystem()
                            .deleteRecursive(dataDir)
                            .onComplete(v -> DemoUtil.gracefulShutdown(vertx));
                      });
            });
  }
}
