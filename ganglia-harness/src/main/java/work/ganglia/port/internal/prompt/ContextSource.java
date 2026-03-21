package work.ganglia.port.internal.prompt;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.chat.SessionContext;

/** Interface for components that provide context fragments. */
public interface ContextSource {
  Future<List<ContextFragment>> getFragments(SessionContext sessionContext);
}
