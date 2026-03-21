package work.ganglia.infrastructure.internal.memory;

import io.vertx.core.Future;
import java.util.Collections;
import java.util.List;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;

/** SRP: Implementation of the ContextCompressor using an LLM to summarize past interactions. */
public class DefaultContextCompressor
    implements work.ganglia.port.internal.memory.ContextCompressor {
  private final ModelGateway model;
  private final ModelConfigProvider configProvider;

  public DefaultContextCompressor(ModelGateway model, ModelConfigProvider configProvider) {
    this.model = model;
    this.configProvider = configProvider;
  }

  public Future<String> summarize(List<Turn> turns, ModelOptions options) {
    if (turns == null || turns.isEmpty()) {
      return Future.succeededFuture("No actions performed.");
    }

    var content =
        new StringBuilder(
            "Please summarize the following interaction history into a concise, single-sentence result description.\n\n");
    for (Turn t : turns) {
      for (var m : t.flatten()) {
        content.append(m.role()).append(": ").append(m.content()).append("\n");
      }
    }

    content.append("\nSummary:");

    var userMsg = Message.user(content.toString());

    // Use utility model from config if available
    ModelOptions summaryOptions = options;
    if (configProvider != null) {
      summaryOptions =
          new ModelOptions(
              configProvider.getTemperature(),
              configProvider.getMaxTokens(),
              configProvider.getUtilityModel(),
              configProvider.isUtilityStream());
    }

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }

  @Override
  public Future<String> reflect(Turn turn) {
    String content = turn.toString();
    String prompt =
        "Review the following interaction and extract key learnings, state changes, or user preferences. "
            + "Output ONLY the extracted facts as a short bulleted list.\n\n"
            + content;

    var userMsg = Message.user(prompt);
    ModelOptions summaryOptions =
        new ModelOptions(0.0, 1024, configProvider.getUtilityModel(), false);

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }

  @Override
  public Future<String> compress(List<Turn> turns) {
    var content = new StringBuilder("Summarize these turns into a dense state report:\n");
    for (Turn t : turns) content.append(t.toString()).append("\n");

    var userMsg = Message.user(content.toString());
    ModelOptions summaryOptions =
        new ModelOptions(0.0, 2048, configProvider.getUtilityModel(), false);

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }
}
