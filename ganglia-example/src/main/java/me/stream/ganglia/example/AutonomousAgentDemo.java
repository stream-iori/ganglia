package me.stream.ganglia.example;

import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.ui.TerminalUI;

import java.util.UUID;

/**
 * A demo showcasing the autonomous Standard (Reasoning and Acting) loop.
 * The agent receives a single complex goal and must decide which tools
 * to use in sequence to achieve it.
 */
public class AutonomousAgentDemo {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                DemoUtil.gracefulShutdown(vertx);
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = TerminalUI.create(vertx);
                String sessionId = "agent-demo-" + UUID.randomUUID().toString().substring(0, 8);

                System.out.println("--- Ganglia Autonomous Standard Demo ---");
                System.out.println("Session ID: " + sessionId);

                // 1. Setup initial state: Create a file for the agent to find
                vertx.fileSystem().writeFile("react_test_data.txt",
                        io.vertx.core.buffer.Buffer.buffer("Task: Sum these numbers: 123, 456, 789"))
                    .onSuccess(v -> {
                        // 2. Start streaming
                        ui.listenToStream(sessionId);

                        // 3. Single high-level prompt requiring multiple steps:
                        // Discover -> Read -> Process -> Write -> Delete
                        String input = "Find the file named 'react_test_data.txt', " +
                            "read its content, calculate the sum of numbers in it, " +
                            "write the result into a new file 'react_result.txt', " +
                            "verify 'react_result.txt' exists, then delete BOTH files.";

                        System.out.println("\nUser: " + input);
                        System.out.println("\n--- Agent Autonomous Reasoning Loop Start ---");
                        System.out.print("Agent: ");

                        ganglia.sessionManager().getSession(sessionId)
                            .compose(context -> ganglia.agentLoop().run(input, context))
                            .onComplete(ar -> {
                                if (ar.succeeded()) {
                                    System.out.println("\n\n--- Workflow Complete ---");
                                    System.out.println("Final Agent Response: " + ar.result());
                                } else {
                                    System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
                                }
                                DemoUtil.gracefulShutdown(vertx);
                            });
                    })
                    .onFailure(err -> {
                        System.err.println("Setup failed: " + err.getMessage());
                        DemoUtil.gracefulShutdown(vertx);
                    });
            });
    }
}
