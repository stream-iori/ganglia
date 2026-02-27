package me.stream.ganglia.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.ui.TerminalUI;

import java.util.UUID;

/**
 * A complex demo showing a multi-turn workflow with tool usage and context persistence.
 */
public class ComplexWorkflowDemo {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                DemoUtil.gracefulShutdown(vertx);
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = TerminalUI.create(vertx);
                String sessionId = "complex-demo-" + UUID.randomUUID().toString().substring(0, 8);

                System.out.println("--- Ganglia Complex Workflow Demo ---");
                System.out.println("Session ID: " + sessionId);

                // Start listening to the streaming output
                ui.listenToStream(sessionId);

                // Turn 1: Create a file
                String input1 = "Create a temporary file named 'complex_demo.txt' with the text 'Initial content.' using shell command.";

                runTurn(ganglia, sessionId, input1)
                    .compose(context1 -> {
                        // Turn 2: Append content and read back
                        System.out.println("\n\n--- Turn 2 ---");
                        String input2 = "Append ' Second line added.' to 'complex_demo.txt', then read the file to confirm content.";
                        return runTurn(ganglia, sessionId, input2);
                    })
                    .compose(context2 -> {
                        // Turn 3: List directory and delete file
                        System.out.println("\n\n--- Turn 3 ---");
                        String input3 = "Check if 'complex_demo.txt' exists in the current directory using ls, then delete it and confirm deletion.";
                        return runTurn(ganglia, sessionId, input3);
                    })
                    .onComplete(ar -> {
                        if (ar.succeeded()) {
                            System.out.println("\n\n[Complex Workflow Complete]");
                        } else {
                            System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
                        }
                        DemoUtil.gracefulShutdown(vertx);
                    });
            });
    }

    private static Future<SessionContext> runTurn(Ganglia ganglia, String sessionId, String input) {
        System.out.println("\nUser: " + input);
        System.out.print("Agent: ");

        return ganglia.sessionManager().getSession(sessionId)
            .compose(context -> ganglia.agentLoop().run(input, context)
                .compose(response -> {
                    // Return the latest session context for the next turn
                    return ganglia.sessionManager().getSession(sessionId);
                })
            );
    }
}
