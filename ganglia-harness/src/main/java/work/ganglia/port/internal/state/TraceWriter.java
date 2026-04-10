package work.ganglia.port.internal.state;

import io.vertx.core.Future;

/** Abstraction for trace persistence, allowing the implementation to live in a different module. */
public interface TraceWriter {
  Future<Void> close();
}
