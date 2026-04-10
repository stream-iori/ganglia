package work.ganglia.port.internal.prompt;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;

/** Interface for components that provide context fragments. */
public interface ContextSource {
  Future<List<ContextFragment>> getFragments(SessionContext sessionContext);
}
