package work.ganglia.infrastructure.internal.prompt.context;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

/** Provides context fragments about the system environment. */
public class EnvironmentSource implements ContextSource {
  private final Vertx vertx;

  public EnvironmentSource(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    List<ContextFragment> fragments = new ArrayList<>();
    // Environment info is stable during a session, so mark as cacheable
    fragments.add(
        ContextFragment.cacheable(
            "OS", System.getProperty("os.name"), ContextFragment.PRIORITY_ENVIRONMENT));
    fragments.add(
        ContextFragment.cacheable(
            "Working Directory",
            System.getProperty("user.dir"),
            ContextFragment.PRIORITY_ENVIRONMENT));

    // Add simple tree snapshot (top level)
    return vertx
        .fileSystem()
        .readDir(".")
        .map(
            files -> {
              StringBuilder sb = new StringBuilder("Top-level files:\n");
              for (String file : files) {
                String name = file.substring(file.lastIndexOf("/") + 1);
                if (!name.startsWith(".")) {
                  sb.append("- ").append(name).append("\n");
                }
              }
              fragments.add(
                  ContextFragment.cacheable(
                      "Directory Structure", sb.toString(), ContextFragment.PRIORITY_ENVIRONMENT));
              return fragments;
            })
        .recover(err -> Future.succeededFuture(fragments));
  }
}
