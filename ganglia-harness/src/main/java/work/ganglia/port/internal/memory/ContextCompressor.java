package work.ganglia.port.internal.memory;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ModelOptions;

public interface ContextCompressor {
  Future<String> summarize(List<Turn> turns, ModelOptions options);

  Future<String> reflect(Turn turn);

  Future<String> compress(List<Turn> turns);

  /** Extracts structured key facts from a completed turn, appending to the running summary. */
  Future<String> extractKeyFacts(Turn completedTurn, String existingRunningSummary);
}
