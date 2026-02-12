package me.stream.ganglia.core.prompt.context;

import io.vertx.core.Future;
import me.stream.ganglia.core.model.SessionContext;

import java.util.List;

/**
 * Interface for components that provide context fragments.
 */
public interface ContextSource {
    Future<List<ContextFragment>> getFragments(SessionContext sessionContext);
}
