package me.stream.ganglia.example;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.ui.TerminalUI;

import java.util.UUID;

/**
 * A demo showcasing how the ReAct loop handles tool errors (e.g., file not found).
 * The agent is asked to perform a task that starts with a failure and must
 * autonomously decide how to recover or report the error.
 */
public class ErrorHandlingReActDemo {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                DemoUtil.gracefulShutdown(vertx);
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = TerminalUI.create(vertx);
                String sessionId = "error-demo-" + UUID.randomUUID().toString().substring(0, 8);

                System.out.println("--- Ganglia Error Handling ReAct Demo ---");
                System.out.println("Session ID: " + sessionId);

                // 1. Setup: Create a "correct" file but the agent is told the wrong name first
                vertx.fileSystem().writeFile("actual_data.txt",
                        io.vertx.core.buffer.Buffer.buffer("This is the actual data you were looking for."))
                    .onSuccess(v -> {
                        ui.listenToStream(sessionId);

                        // 2. High-level prompt with an intentional error and a prompt for choice
                        String input = """
                            Read the content of 'missing_file.txt'.
                            If it doesn't exist, list the current directory,
                            and use 'ask_selection' to ask me which file looks most relevant before reading it.
                            """;

                        System.out.println("\nUser: " + input);
                        System.out.println("\n--- Agent Autonomous Error Recovery Loop Start ---");
                        System.out.print("Agent: ");

                        ganglia.sessionManager().getSession(sessionId)
                            .compose(context -> runInteractiveLoop(ganglia, sessionId, input, context))
                            .onComplete(ar -> {
                                if (ar.succeeded()) {
                                    System.out.println("\n\n--- Workflow Complete ---");
                                } else {
                                    System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
                                }
                                // Cleanup
                                vertx.fileSystem().delete("actual_data.txt")
                                    .onComplete(v2 -> {
                                        DemoUtil.gracefulShutdown(vertx);
                                    });
                            });
                    })
                    .onFailure(err -> {
                        System.err.println("Setup failed: " + err.getMessage());
                        DemoUtil.gracefulShutdown(vertx);
                    });
            });
    }

    private static Future<Void> runInteractiveLoop(Ganglia ganglia, String sessionId, String input, SessionContext context) {
        return ganglia.agentLoop().run(input, context)
            .compose(response -> {
                return ganglia.sessionManager().getSession(sessionId)
                    .compose(latestContext -> {
                        if (hasPendingInteraction(latestContext)) {
                            System.out.println("\n[INTERACTION REQUIRED]");
                            // For this demo, we simulate user choosing 'actual_data.txt'
                            String feedback = "actual_data.txt";
                            System.out.println("Simulated User Response: " + feedback);

                            return ganglia.agentLoop().resume(feedback, latestContext)
                                .compose(finalResponse -> {
                                    System.out.println("\n\nAgent Final Response: " + finalResponse);
                                    return Future.succeededFuture();
                                });
                        }
                        System.out.println("\n\nAgent Final Response: " + response);
                        return Future.succeededFuture();
                    });
            });
    }

    private static boolean hasPendingInteraction(SessionContext context) {
        if (context.currentTurn() == null) return false;
        var steps = context.currentTurn().intermediateSteps();
        if (steps == null || steps.isEmpty()) return false;

        var lastMsg = steps.get(steps.size() - 1);
        if (lastMsg.role() == me.stream.ganglia.core.model.Role.ASSISTANT && lastMsg.toolCalls() != null) {
            java.util.Set<String> answeredIds = new java.util.HashSet<>();
            for (var m : steps) {
                if (m.role() == me.stream.ganglia.core.model.Role.TOOL && m.toolObservation() != null) {
                    answeredIds.add(m.toolObservation().toolCallId());
                }
            }
            for (var tc : lastMsg.toolCalls()) {
                if (!answeredIds.contains(tc.id())) return true;
            }
        }
        return false;
    }
}
