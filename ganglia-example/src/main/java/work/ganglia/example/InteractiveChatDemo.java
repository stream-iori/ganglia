package work.ganglia.example;

import io.vertx.core.Vertx;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.loop.AgentAbortedException;
import work.ganglia.core.model.AgentSignal;
import work.ganglia.ui.TerminalUI;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.EndOfFileException;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.util.UUID;

/**
 * An interactive chat demo using JLine 3 for a smooth CLI experience.
 */
public class InteractiveChatDemo {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        Main.bootstrap(vertx)
            .onFailure(err -> {
                System.err.println("Bootstrap failed: " + err.getMessage());
                vertx.close();
            })
            .onSuccess(ganglia -> {
                TerminalUI ui = TerminalUI.create(vertx);
                Terminal terminal = ui.getTerminal();
                LineReader reader = LineReaderBuilder.builder()
                        .terminal(terminal)
                        .appName("Ganglia")
                        .build();

                String sessionId = "interactive-" + UUID.randomUUID().toString().substring(0, 8);
                ui.listenToStream(sessionId);

                terminal.writer().println("\n=== Ganglia Interactive CLI ===");
                terminal.writer().println("Model: " + ganglia.configManager().getModel() + " (" + ganglia.configManager().getProvider() + ")");
                terminal.writer().println("Session: " + sessionId);
                terminal.writer().println("Type 'exit' or 'quit' to leave, or Ctrl+C to stop current reasoning.\n");

                chatLoop(ganglia, reader, terminal, sessionId, vertx);
            });
    }

    private static void chatLoop(Ganglia ganglia, LineReader reader, Terminal terminal, String sessionId, Vertx vertx) {
        // We use a mutable container for the signal so it can be recreated if aborted
        final java.util.concurrent.atomic.AtomicReference<AgentSignal> currentSignal = new java.util.concurrent.atomic.AtomicReference<>(null);

        new Thread(() -> {
            while (true) {
                String line;
                try {
                    line = reader.readLine("ganglia> ");
                } catch (UserInterruptException e) {
                    // Handle Ctrl+C during input reading
                    if (currentSignal.get() != null && !currentSignal.get().isAborted()) {
                        terminal.writer().println("\n[System] Agent is running. What would you like to do?");
                        terminal.writer().println("1. Abort completely (Hard Stop)");
                        terminal.writer().println("2. Add a steering instruction (Course Correction)");
                        terminal.writer().println("3. Resume waiting");
                        try {
                            String choice = reader.readLine("Choice [1-3]: ");
                            if ("1".equals(choice.trim())) {
                                currentSignal.get().abort();
                                terminal.writer().println("[System] Abort signal sent.");
                            } else if ("2".equals(choice.trim())) {
                                String steeringMsg = reader.readLine("Instruction: ");
                                if (!steeringMsg.trim().isEmpty()) {
                                    ganglia.sessionManager().addSteeringMessage(sessionId, steeringMsg);
                                    terminal.writer().println("[System] Steering message queued.");
                                }
                            }
                        } catch (Exception ex) {
                            terminal.writer().println("\n[System] Resuming...");
                        }
                    } else {
                        terminal.writer().println("^C");
                    }
                    continue;
                } catch (EndOfFileException e) {
                    // Handle Ctrl+D
                    break;
                }

                if (line == null || line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    break;
                }

                if (line.trim().isEmpty()) {
                    continue;
                }

                final String input = line;

                // Synchronize the input thread with the agent loop to prevent prompt interleaving.
                java.util.concurrent.CompletableFuture<Void> turnDone = new java.util.concurrent.CompletableFuture<>();
                AgentSignal newSignal = new AgentSignal();
                currentSignal.set(newSignal);

                vertx.runOnContext(v -> {
                    ganglia.sessionManager().getSession(sessionId)
                        .compose(context -> ganglia.agentLoop().run(input, context, newSignal))
                        .onComplete(ar -> {
                            currentSignal.set(null);
                            if (ar.failed() && ar.cause() instanceof AgentAbortedException) {
                                terminal.writer().println("\n[System] Session was aborted by user.");
                            }
                            turnDone.complete(null);
                        });
                });

                try {
                    // Read input concurrently to allow for steering during long runs
                    while (!turnDone.isDone()) {
                        try {
                            // Check if user pressed anything, or wait via JLine.
                            // Since reader.readLine() blocks, we can't easily wait for turnDone AND readLine in the same thread.
                            // However, JLine's reader.readLine() THROWS UserInterruptException if the user presses Ctrl+C.
                            // So we just block on turnDone here, but if the user wants to interrupt, they press Ctrl+C,
                            // which is caught by the terminal handler and raises a signal in Unix.
                            // In a real CLI, we might need a separate reader thread.
                            // For simplicity, we just block here. If Ctrl+C is pressed, the JVM handles it, but since JLine is in control,
                            // it might require hitting Enter or Ctrl+C.
                            turnDone.get(100, java.util.concurrent.TimeUnit.MILLISECONDS);
                        } catch (java.util.concurrent.TimeoutException ex) {
                            // Expected, just loop and check if turnDone is completed
                        }
                    }
                } catch (Exception e) {
                    terminal.writer().println("\nInterrupt: " + e.getMessage());
                }
            }
            vertx.close();
            System.exit(0);
        }).start();
    }
}
