package work.ganglia.infrastructure.internal.prompt.context;

import io.vertx.core.Future;
import java.util.List;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.GuidelineContextSource;

/**
 * Core abstraction for providing operational mandates. Mandates represent hard rules that the agent
 * must strictly follow.
 */
public abstract class MandatesContextSource implements GuidelineContextSource {

  protected abstract String getMandates();

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    String mandates = getMandates();
    if (mandates == null || mandates.isBlank()) {
      return Future.succeededFuture(List.of());
    }
    return Future.succeededFuture(
        List.of(
            ContextFragment.mandatory("Mandates", mandates, ContextFragment.PRIORITY_MANDATES)));
  }
}
