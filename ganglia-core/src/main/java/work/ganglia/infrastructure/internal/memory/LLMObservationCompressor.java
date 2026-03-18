package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Role;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.state.AgentSignal;

import java.util.Collections;
import java.util.List;

public class LLMObservationCompressor implements ObservationCompressor {
    
    private final ModelGateway modelGateway;
    private final int thresholdLength; // e.g. 4000 chars

    public LLMObservationCompressor(ModelGateway modelGateway, int thresholdLength) {
        this.modelGateway = modelGateway;
        this.thresholdLength = thresholdLength;
    }

    @Override
    public boolean requiresCompression(String rawOutput) {
        return rawOutput != null && rawOutput.length() > thresholdLength;
    }

    @Override
    public Future<String> compress(String rawOutput, CompressionContext context) {
        if (!requiresCompression(rawOutput)) {
            return Future.succeededFuture(rawOutput);
        }

        String prompt = String.format(
            "Compress the following raw output from the tool '%s'.\n" +
            "Current Task: %s\n" +
            "Extract the most essential information, findings, or error messages relevant to the task.\n" +
            "Keep the summary strictly under %d tokens (roughly %d words).\n" +
            "Do not add conversational filler. Be direct and precise.\n\n" +
            "--- RAW OUTPUT ---\n" +
            "%s\n" +
            "------------------\n",
            context.toolName(),
            context.currentTaskDescription(),
            context.maxTokens(),
            (int) (context.maxTokens() * 0.75),
            rawOutput
        );

        Message message = Message.user(prompt);
        ChatRequest request = new ChatRequest(
            List.of(message),
            Collections.emptyList(),
            new ModelOptions(0.0, 1024, "gpt-4o-mini", false), // Or make model configurable
            new AgentSignal()
        );

        return modelGateway.chat(request)
            .map(ModelResponse::content);
    }
}