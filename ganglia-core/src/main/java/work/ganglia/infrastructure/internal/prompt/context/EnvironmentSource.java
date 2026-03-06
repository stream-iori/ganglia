package work.ganglia.infrastructure.internal.prompt.context;

import work.ganglia.port.internal.prompt.ContextFragment;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides context fragments about the system environment.
 */
public class EnvironmentSource implements ContextSource {
    private final Vertx vertx;

    public EnvironmentSource(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
        List<ContextFragment> fragments = new ArrayList<>();
        fragments.add(new ContextFragment("OS", System.getProperty("os.name"), ContextFragment.PRIORITY_ENVIRONMENT, true));
        fragments.add(new ContextFragment("Working Directory", System.getProperty("user.dir"), ContextFragment.PRIORITY_ENVIRONMENT, true));

        // Add simple tree snapshot (top level)
        return vertx.fileSystem().readDir(".")
                .map(files -> {
                    StringBuilder sb = new StringBuilder("Top-level files:\n");
                    for (String file : files) {
                        String name = file.substring(file.lastIndexOf("/") + 1);
                        if (!name.startsWith(".")) {
                            sb.append("- ").append(name).append("\n");
                        }
                    }
                    fragments.add(new ContextFragment("Directory Structure", sb.toString(), ContextFragment.PRIORITY_ENVIRONMENT, false));
                    return fragments;
                })
                .recover(err -> Future.succeededFuture(fragments));
    }
}
