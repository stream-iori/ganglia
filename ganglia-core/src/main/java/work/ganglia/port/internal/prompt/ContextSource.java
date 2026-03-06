package work.ganglia.port.internal.prompt;

import io.vertx.core.Future;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.infrastructure.internal.prompt.context.ContextFragment;

import java.util.List;

/**
 * Interface for components that provide context fragments.
 */
public interface ContextSource {
    Future<List<ContextFragment>> getFragments(SessionContext sessionContext);
}
