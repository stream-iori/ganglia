package me.stream.ganglia.memory;

import io.vertx.core.Future;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;

import java.util.Collections;
import java.util.List;

public class ContextCompressor {
    private final ModelGateway model;
    private final ConfigManager configManager;

    public ContextCompressor(ModelGateway model, ConfigManager configManager) {
        this.model = model;
        this.configManager = configManager;
    }

    public Future<String> summarize(List<Turn> turns, ModelOptions options) {
        if (turns == null || turns.isEmpty()) {
            return Future.succeededFuture("No actions performed.");
        }

        var content = new StringBuilder("Please summarize the following interaction history into a concise, single-sentence result description.\n\n");
        for (Turn t : turns) {
            for (var m : t.flatten()) {
                content.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }

        content.append("\nSummary:");

        var userMsg = Message.user(content.toString());

        // Use utility model from config if available
        ModelOptions summaryOptions = options;
        if (configManager != null) {
            summaryOptions = new ModelOptions(
                    configManager.getTemperature(),
                    configManager.getMaxTokens(),
                    configManager.getUtilityModel()
            );
        }

        return model.chat(List.of(userMsg), Collections.emptyList(), summaryOptions)
                .map(ModelResponse::content);
    }

    /**
     * Reflects on a single turn to extract concise accomplishments.
     */
    public Future<String> reflect(Turn turn) {
        if (turn == null) return Future.succeededFuture("");

        var content = new StringBuilder("Please extract the technical accomplishments and key facts from the following interaction. " +
                "Format as a concise bulleted list. Focus on WHAT was changed or learned.\n\n");
        
        for (var m : turn.flatten()) {
            content.append(m.role()).append(": ").append(m.content()).append("\n");
        }

        content.append("\nAccomplishments:");

        var userMsg = Message.user(content.toString());

        ModelOptions summaryOptions = new ModelOptions(
                configManager.getTemperature(),
                configManager.getMaxTokens(),
                configManager.getUtilityModel()
        );

        return model.chat(List.of(userMsg), Collections.emptyList(), summaryOptions)
                .map(ModelResponse::content);
    }
}
