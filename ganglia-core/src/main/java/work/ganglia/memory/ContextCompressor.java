package work.ganglia.memory;

import io.vertx.core.Future;
import work.ganglia.core.config.ConfigManager;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.model.Message;
import work.ganglia.core.model.ModelOptions;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.Turn;

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

        var future = model.chat(List.of(userMsg), Collections.emptyList(), summaryOptions);
        if (future == null) {
            return Future.failedFuture("Model gateway returned null for LLM call.");
        }
        return future.map(ModelResponse::content);
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

        Future<ModelResponse> future = model.chat(List.of(userMsg), Collections.emptyList(), summaryOptions);
        if (future == null) {
            return Future.failedFuture("Model gateway returned null for LLM call.");
        }
        return future.map(ModelResponse::content);
    }

    /**
     * Compresses a list of turns into a dense state summary.
     */
    public Future<String> compress(List<Turn> turns) {
        if (turns == null || turns.isEmpty()) {
            return Future.succeededFuture("No previous context.");
        }

        var content = new StringBuilder("""
            You are a system context compressor. Your goal is to summarize the following agent interaction history into a dense 'State Summary'.
            This summary will replace the raw history in the agent's context window.

            Preserve:
            1. Key facts discovered (e.g. file paths, API endpoints, error messages).
            2. Completed tasks and technical accomplishments.
            3. Any critical constraints or user preferences mentioned.
            4. Current state of the environment (if known).

            Avoid:
            - Conversational filler.
            - Repetitive 'Thought' blocks.
            - Large raw tool outputs (just summarize what they revealed).

            HISTORY TO SUMMARIZE:
            """);

        for (Turn t : turns) {
            content.append("--- Turn ").append(t.id()).append(" ---\n");
            for (var m : t.flatten()) {
                content.append("[").append(m.role()).append("] ").append(m.content()).append("\n");
            }
        }

        content.append("\nGENERATE DENSE STATE SUMMARY:");

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
