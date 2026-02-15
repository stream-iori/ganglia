package me.stream.ganglia.ui;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import me.stream.ganglia.core.model.ObservationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalUI {
    private static final Logger logger = LoggerFactory.getLogger(TerminalUI.class);
    private final Vertx vertx;

    public TerminalUI(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Subscribes to the event bus for observations and prints them to stdout.
     */
    public MessageConsumer<JsonObject> listenToStream(String sessionId) {
        String address = "ganglia.observations." + sessionId;
        return vertx.eventBus().consumer(address, message -> {
            ObservationEvent event = message.body().mapTo(ObservationEvent.class);
            renderEvent(event);
        });
    }

    private void renderEvent(ObservationEvent event) {
        switch (event.type()) {
            case TOKEN_RECEIVED:
                if (event.content() != null) {
                    System.out.print(event.content());
                    System.out.flush();
                }
                break;
            case TOOL_STARTED:
                System.out.println("\n\u001B[33m[Tool Call: " + event.content() + "]\u001B[0m");
                break;
            case TOOL_FINISHED:
                System.out.println("\u001B[32m[Tool Finished]\u001B[0m");
                break;
            case ERROR:
                System.err.println("\n\u001B[31m[Error: " + event.content() + "]\u001B[0m");
                break;
            case TURN_FINISHED:
                System.out.println();
                break;
            default:
                // Other events might not need direct console output or are handled by thought streaming
                break;
        }
    }
}
