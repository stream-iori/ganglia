package me.stream.ganglia.example;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.ui.TerminalUI;

import java.util.Scanner;
import java.util.UUID;
import java.util.Set;

/**
 * A demo showcasing interactive troubleshooting with ask_selection.
 * This demo uses the EventBus to handle user input asynchronously, 
 * avoiding blocking the Vert.x event loop.
 */
public class InteractiveDemo {

    private static final String INPUT_ADDRESS = "user.input.stdin";

    public static void main(String[] args) {
        // Increase blocked thread check interval and max execute time to 60s 
        // to avoid warnings during manual interaction or long reasoning turns.
        VertxOptions options = new VertxOptions()
            .setBlockedThreadCheckInterval(60000)
            .setMaxEventLoopExecuteTime(60000)
            .setMaxWorkerExecuteTime(60000);
        Vertx vertx = Vertx.vertx(options);

        // Start a dedicated thread for reading from stdin to keep Vert.x reactive
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line.trim()) || "quit".equalsIgnoreCase(line.trim())) {
                    System.out.println("Exiting...");
                    vertx.close();
                    System.exit(0);
                }
                vertx.eventBus().publish(INPUT_ADDRESS, line);
            }
        }, "stdin-reader-thread").start();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = new TerminalUI(vertx);
                String sessionId = "interactive-demo-" + UUID.randomUUID().toString().substring(0, 8);

                System.out.println("--- Ganglia Interactive Demo ---");
                System.out.println("Session ID: " + sessionId);
                System.out.println("Commands: 'exit' or 'quit' to stop.");
                System.out.println("Type your goal (e.g., 'Find all files and ask me which one to read')");
                
                // Established stream listener ONCE to avoid duplicate output
                ui.listenToStream(sessionId);

                System.out.print("\nUser: ");
                handleNextUserTurn(vertx, ganglia, sessionId);
            });
    }

    private static void handleNextUserTurn(Vertx vertx, Ganglia ganglia, String sessionId) {
        waitForInput(vertx).onSuccess(userInput -> {
            System.out.print("Agent: ");
            ganglia.sessionManager().getSession(sessionId)
                .compose(context -> ganglia.agentLoop().run(userInput, context))
                .onComplete(ar -> handleAgentResponse(vertx, ganglia, sessionId, ar));
        });
    }

    private static void handleAgentResponse(Vertx vertx, Ganglia ganglia, String sessionId, io.vertx.core.AsyncResult<String> ar) {
        if (ar.failed()) {
            System.err.println("\n\nWorkflow Error: " + ar.cause().getMessage());
            System.out.print("\nUser: ");
            handleNextUserTurn(vertx, ganglia, sessionId);
            return;
        }

        ganglia.sessionManager().getSession(sessionId).onSuccess(context -> {
            if (hasPendingInteraction(context)) {
                System.out.println("\n[INTERACTION REQUIRED]");
                System.out.print("Your response: ");
                
                waitForInput(vertx).onSuccess(feedback -> {
                    System.out.print("Agent: ");
                    // Resume the loop with user feedback
                    ganglia.agentLoop().resume(feedback, context)
                        .onComplete(res -> handleAgentResponse(vertx, ganglia, sessionId, res));
                });
            } else {
                // Turn is fully finished
                System.out.println("\n\n--- Workflow Complete ---");
                System.out.println("\n--- Turn Complete ---");
                System.out.print("\nUser: ");
                handleNextUserTurn(vertx, ganglia, sessionId);
            }
        });
    }

    private static Future<String> waitForInput(Vertx vertx) {
        Promise<String> promise = Promise.promise();
        var consumer = vertx.eventBus().<String>consumer(INPUT_ADDRESS);
        consumer.handler(msg -> {
            consumer.unregister();
            promise.complete(msg.body());
        });
        return promise.future();
    }

    private static boolean hasPendingInteraction(SessionContext context) {
        if (context.currentTurn() == null) return false;
        var steps = context.currentTurn().intermediateSteps();
        if (steps == null || steps.isEmpty()) return false;

        // Collect all answered tool call IDs
        Set<String> answeredIds = new java.util.HashSet<>();
        for (var m : steps) {
            if (m.role() == me.stream.ganglia.core.model.Role.TOOL && m.toolObservation() != null) {
                answeredIds.add(m.toolObservation().toolCallId());
            }
        }

        // Find if any assistant message has an unanswered tool call
        for (var m : steps) {
            if (m.role() == me.stream.ganglia.core.model.Role.ASSISTANT && m.toolCalls() != null) {
                for (var tc : m.toolCalls()) {
                    if (!answeredIds.contains(tc.id())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
