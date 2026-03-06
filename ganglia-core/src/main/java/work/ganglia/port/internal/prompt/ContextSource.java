package work.ganglia.port.internal.prompt;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;

import java.util.List;

/**
 * Interface for components that provide context fragments.
 */
public interface ContextSource {
    Future<List<ContextFragment>> getFragments(SessionContext sessionContext);
}
