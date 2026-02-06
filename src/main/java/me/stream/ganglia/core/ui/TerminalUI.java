package me.stream.ganglia.core.ui;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalUI {
    private static final Logger logger = LoggerFactory.getLogger(TerminalUI.class);
    private final Vertx vertx;

    public TerminalUI(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Subscribes to the event bus for streaming tokens and prints them to stdout.
     */
    public MessageConsumer<String> listenToStream(String sessionId) {
        String address = "ganglia.stream." + sessionId;
        return vertx.eventBus().consumer(address, message -> {
            String token = message.body();
            if (token != null) {
                System.out.print(token);
                System.out.flush();
            }
        });
    }
}
