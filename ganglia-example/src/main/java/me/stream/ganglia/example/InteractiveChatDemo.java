package me.stream.ganglia.example;

import io.vertx.core.Vertx;
import me.stream.Main;
import me.stream.ganglia.core.Ganglia;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.ui.TerminalUI;
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
        new Thread(() -> {
            while (true) {
                String line;
                try {
                    line = reader.readLine("ganglia> ");
                } catch (UserInterruptException e) {
                    // Handle Ctrl+C
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

                vertx.runOnContext(v -> {
                    ganglia.sessionManager().getSession(sessionId)
                        .compose(context -> ganglia.agentLoop().run(input, context))
                        .onComplete(ar -> {
                            turnDone.complete(null);
                        });
                });

                try {
                    // Block the chat thread until the Vert.x event loop finishes this turn.
                    turnDone.get();
                } catch (Exception e) {
                    terminal.writer().println("\nInterrupt: " + e.getMessage());
                }
            }
            vertx.close();
            System.exit(0);
        }).start();
    }
}
