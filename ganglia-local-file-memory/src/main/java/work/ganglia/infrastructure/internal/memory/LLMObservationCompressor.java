package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import work.ganglia.port.chat.Message;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.memory.ObservationCompressor;
import work.ganglia.port.internal.memory.model.CompressionContext;
import work.ganglia.port.internal.state.AgentSignal;

public class LLMObservationCompressor implements ObservationCompressor {

  private final ModelGateway modelGateway;
  private final int thresholdLength; // e.g. 4000 chars
  private final String modelName;

  public LLMObservationCompressor(ModelGateway modelGateway, int thresholdLength) {
    this(modelGateway, thresholdLength, "gpt-4o-mini");
  }

  public LLMObservationCompressor(
      ModelGateway modelGateway, int thresholdLength, String modelName) {
    this.modelGateway = modelGateway;
    this.thresholdLength = thresholdLength;
    this.modelName = (modelName != null && !modelName.isEmpty()) ? modelName : "gpt-4o-mini";
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
            (int) (context.maxTokens() * 0.75),
            rawOutput);

    Message message = Message.user(prompt);
    ChatRequest request =
        new ChatRequest(
            List.of(message),
            Collections.emptyList(),
            new ModelOptions(0.0, 1024, modelName, false),
            new AgentSignal());

    return modelGateway.chat(request).map(ModelResponse::content);
  }
}
