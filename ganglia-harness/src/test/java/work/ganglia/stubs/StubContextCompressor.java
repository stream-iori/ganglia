package work.ganglia.stubs;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.internal.memory.ContextCompressor;

/** Stub implementation of ContextCompressor for tests in ganglia-harness. */
public class StubContextCompressor implements ContextCompressor {

  @Override
  public Future<String> summarize(List<Turn> turns, ModelOptions options) {
    return Future.succeededFuture("stub summary");
  }

  @Override
  public Future<String> reflect(Turn turn) {
    return Future.succeededFuture("stub reflection");
  }

  @Override
  public Future<String> compress(List<Turn> turns) {
    return Future.succeededFuture("stub compression");
  }
}
