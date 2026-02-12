package me.stream.ganglia.memory;

import io.vertx.core.Future;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ContextCompressor {
    private final ModelGateway model;

    public ContextCompressor(ModelGateway model) {
        this.model = model;
    }

    public Future<String> summarize(List<Turn> turns, ModelOptions options) {
        if (turns == null || turns.isEmpty()) {
            return Future.succeededFuture("No actions performed.");
        }

        StringBuilder content = new StringBuilder("Please summarize the following interaction history into a concise, single-sentence result description.\n\n");
        for (Turn t : turns) {
            for (Message m : t.flatten()) {
                content.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }

        content.append("\nSummary:");

        Message userMsg = Message.user(content.toString());
        // We use a temporary context for summarization
        // Ideally we should use a "cheaper" model if possible, but we use 'options' passed in.

        return model.chat(List.of(userMsg), Collections.emptyList(), options)
                .map(ModelResponse::content);
    }
}
