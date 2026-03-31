package work.ganglia.infrastructure.internal.memory;

import java.util.Collections;
import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.prompt.CompressionBudget;
import work.ganglia.port.internal.state.AgentSignal;

public class LLMObservationCompressor implements ObservationCompressor {

  private final ModelGateway modelGateway;
  private final int thresholdLength; // e.g. 4000 chars
  private final String modelName;
  private final CompressionBudget compressionBudget;

  public LLMObservationCompressor(ModelGateway modelGateway, int thresholdLength) {
    this(modelGateway, thresholdLength, null, CompressionBudget.defaults());
  }

  public LLMObservationCompressor(
      ModelGateway modelGateway, int thresholdLength, String modelName) {
    this(modelGateway, thresholdLength, modelName, CompressionBudget.defaults());
  }

  public LLMObservationCompressor(
      ModelGateway modelGateway,
      int thresholdLength,
      String modelName,
      CompressionBudget compressionBudget) {
    this.modelGateway = modelGateway;
    this.thresholdLength = thresholdLength;
    this.modelName = modelName;
    this.compressionBudget = compressionBudget;
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

    // Approximate word count: tokens × 0.75 is a rough average for English text
    int approxWords = (int) (context.maxTokens() * 0.75);
    String prompt =
        String.format(
            "Compress the following raw output from the tool '%s'.\n"
                + "Current Task: %s\n"
                + "Extract the most essential information, findings, or error messages relevant to the task.\n"
                + "Keep the summary strictly under %d tokens (roughly %d words).\n"
                + "Do not add conversational filler. Be direct and precise.\n\n"
                + "--- RAW OUTPUT ---\n"
                + "%s\n"
                + "------------------\n",
            context.toolName(),
            context.currentTaskDescription(),
            context.maxTokens(),
            approxWords,
            rawOutput);

    Message message = Message.user(prompt);
    ChatRequest request =
        new ChatRequest(
            List.of(message),
            Collections.emptyList(),
            new ModelOptions(
                0.0, compressionBudget.observationCompressMaxTokens(), modelName, false),
            new AgentSignal());

    return modelGateway.chat(request).map(ModelResponse::content);
  }
}
