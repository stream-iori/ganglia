package work.ganglia.infrastructure.internal.prompt.context;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import java.util.List;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

/** Loads context fragments from a specific file. */
public class FileContextSource implements ContextSource {
  private final Vertx vertx;
  private final MarkdownContextResolver resolver;
  private final String filePath;

  public FileContextSource(Vertx vertx, MarkdownContextResolver resolver, String filePath) {
    this.vertx = vertx;
    this.resolver = resolver;
    this.filePath = filePath;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    return vertx
        .fileSystem()
        .exists(filePath)
        .compose(
            exists -> {
              if (exists) {
                return vertx
                    .fileSystem()
                    .readFile(filePath)
                    .compose(buffer -> resolver.parse(filePath, buffer.toString()));
              } else {
                // If the instruction file is specified but missing, provide a placeholder fragment
                // so the system prompt contains the filename (important for tests and debugging).
                return Future.succeededFuture(
                    List.of(
                        ContextFragment.mandatory(
                            "Instruction File Status",
                            "Specified instruction file '" + filePath + "' was not found.",
                            ContextFragment.PRIORITY_MANDATES)));
              }
            });
  }
}
