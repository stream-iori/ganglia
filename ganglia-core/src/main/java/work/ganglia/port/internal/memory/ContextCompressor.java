package work.ganglia.port.internal.memory;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ModelOptions;

public interface ContextCompressor {
  Future<String> summarize(List<Turn> turns, ModelOptions options);

  Future<String> reflect(Turn turn);

  Future<String> compress(List<Turn> turns);
}
