package me.stream.ganglia.memory;

import io.vertx.core.Future;
import me.stream.ganglia.core.config.ConfigManager;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

        StringBuilder content = new StringBuilder("Please summarize the following interaction history into a concise, single-sentence result description.\n\n");
        for (Turn t : turns) {
            for (Message m : t.flatten()) {
                content.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }

        content.append("\nSummary:");

        Message userMsg = Message.user(content.toString());
        
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
}
