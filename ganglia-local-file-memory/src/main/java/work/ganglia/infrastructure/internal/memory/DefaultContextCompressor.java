package work.ganglia.infrastructure.internal.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;

import work.ganglia.config.ModelConfigProvider;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.util.TokenCounter;

/** SRP: Implementation of the ContextCompressor using an LLM to summarize past interactions. */
public class DefaultContextCompressor
    implements work.ganglia.port.internal.memory.ContextCompressor {
  private final ModelGateway model;
  private final ModelConfigProvider configProvider;
  private final TokenCounter tokenCounter;

  public DefaultContextCompressor(ModelGateway model, ModelConfigProvider configProvider) {
    this(model, configProvider, new TokenCounter());
  }

  public DefaultContextCompressor(
      ModelGateway model, ModelConfigProvider configProvider, TokenCounter tokenCounter) {
    this.model = model;
    this.configProvider = configProvider;
    this.tokenCounter = tokenCounter;
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
    String fullText = turnsToText(turns);
    int totalTokens = tokenCounter.count(fullText);
    int utilityLimit = configProvider.getUtilityContextLimit();

    if (totalTokens < (int) (utilityLimit * 0.8)) {
      return compressSingle(turns);
    }

    // Split into chunks and compress each, then merge
    List<List<Turn>> chunks = splitByTokenBudget(turns, (int) (utilityLimit * 0.6));
    List<Future<String>> chunkFutures =
        chunks.stream().map(this::compressSingle).collect(Collectors.toList());

    return Future.join(chunkFutures)
        .compose(
            cf -> {
              List<String> summaries = new ArrayList<>();
              for (Future<String> f : chunkFutures) {
                summaries.add(f.result());
              }
              String merged = String.join("\n\n---\n\n", summaries);
              int mergedTokens = tokenCounter.count(merged);
              if (mergedTokens > (int) (utilityLimit * 0.8)) {
                // Merge summaries are still too large, compress once more
                return compressText(merged);
              }
              return compressText(merged);
            });
  }

  @Override
  public Future<String> extractKeyFacts(Turn completedTurn, String existingRunningSummary) {
    var content =
        new StringBuilder(
            """
            Given the existing running summary and the latest completed interaction turn,
            extract and append NEW key facts. Output format:
            - [DECISION] ...
            - [FILE] ...
            - [ERROR] ...
            - [PREFERENCE] ...
            - [STATE] ...
            Keep the total summary under 1500 tokens. Remove outdated facts if needed.

            """);
    if (existingRunningSummary != null && !existingRunningSummary.isBlank()) {
      content.append("Existing running summary:\n").append(existingRunningSummary).append("\n\n");
    }
    content.append("Latest turn:\n").append(completedTurn.toString());

    var userMsg = Message.user(content.toString());
    ModelOptions summaryOptions =
        new ModelOptions(0.0, 2048, configProvider.getUtilityModel(), false);

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }

  private Future<String> compressSingle(List<Turn> turns) {
    var content = new StringBuilder(STRUCTURED_COMPRESS_PROMPT);
    for (Turn t : turns) {
      content.append(t.toString()).append("\n");
    }

    var userMsg = Message.user(content.toString());
    ModelOptions summaryOptions =
        new ModelOptions(0.0, 2048, configProvider.getUtilityModel(), false);

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }

  private Future<String> compressText(String text) {
    String prompt = STRUCTURED_COMPRESS_PROMPT + text;
    var userMsg = Message.user(prompt);
    ModelOptions summaryOptions =
        new ModelOptions(0.0, 2048, configProvider.getUtilityModel(), false);

    ChatRequest request =
        new ChatRequest(
            List.of(userMsg), Collections.emptyList(), summaryOptions, new AgentSignal());
    return model.chat(request).map(ModelResponse::content);
  }

  private String turnsToText(List<Turn> turns) {
    var sb = new StringBuilder();
    for (Turn t : turns) {
      sb.append(t.toString()).append("\n");
    }
    return sb.toString();
  }

  private List<List<Turn>> splitByTokenBudget(List<Turn> turns, int chunkTokenLimit) {
    List<List<Turn>> chunks = new ArrayList<>();
    List<Turn> currentChunk = new ArrayList<>();
    int currentTokens = 0;

    for (Turn t : turns) {
      int turnTokens = tokenCounter.count(t.toString());
      if (!currentChunk.isEmpty() && currentTokens + turnTokens > chunkTokenLimit) {
        chunks.add(currentChunk);
        currentChunk = new ArrayList<>();
        currentTokens = 0;
      }
      currentChunk.add(t);
      currentTokens += turnTokens;
    }
    if (!currentChunk.isEmpty()) {
      chunks.add(currentChunk);
    }
    return chunks;
  }

  private static final String STRUCTURED_COMPRESS_PROMPT =
      """
      Summarize the following agent interaction turns into a structured state report.

      CRITICAL: You MUST preserve the following information verbatim (do not paraphrase):
      - File paths and line numbers mentioned
      - Error messages, stack traces, and error codes
      - User decisions and explicit preferences
      - Tool invocation results that changed system state (file edits, command outputs)
      - Variable names, function names, and code identifiers

      Output format:
      ## Key Decisions
      - [list user decisions and preferences]

      ## Files Modified
      - [list file paths with what was changed]

      ## Errors Encountered
      - [list errors and their resolutions, or "None"]

      ## Current State
      - [describe the current state of the task]

      ## Facts & Context
      - [other important facts for continuing the conversation]

      Keep the total output under 1500 tokens.

      ---
      Interaction turns:
      """;
}
