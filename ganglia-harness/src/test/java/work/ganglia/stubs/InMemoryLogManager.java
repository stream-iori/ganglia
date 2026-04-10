package work.ganglia.stubs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.LogManager;

public class InMemoryLogManager implements LogManager {

  private final List<SessionContext> logs = Collections.synchronizedList(new ArrayList<>());

  @Override
  public Future<Void> appendLog(SessionContext context) {
    logs.add(context);
    return Future.succeededFuture();
  }

  // Test helper
  public List<SessionContext> getLogs() {
    return logs;
  }
}
