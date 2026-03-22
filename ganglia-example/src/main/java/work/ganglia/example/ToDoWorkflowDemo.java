package work.ganglia.example;

import io.vertx.core.Vertx;
import java.util.UUID;
import work.ganglia.Ganglia;
import work.ganglia.ui.TerminalUi;

/** Demo showcasing the agent's ability to plan, execute, and complete tasks using ToDoTools. */
public class ToDoWorkflowDemo {

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
                        System.out.println("--- Ganglia CLI ToDo Workflow Demo ---");
                        System.out.println("Session ID: " + sessionId);

                        String input =
                            args.length > 0
                                ? String.join(" ", args)
                                : "Please create a plan with 2 steps using your todo tools: 1. echo 'Hello ToDo' to a file named 'todo-test.txt'. 2. Read the file. Execute the plan step by step, and make sure to use todo_complete to mark tasks as done as you progress.";
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
