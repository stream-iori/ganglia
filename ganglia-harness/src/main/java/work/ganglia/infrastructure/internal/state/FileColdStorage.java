package work.ganglia.infrastructure.internal.state;

import java.nio.file.Path;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

import work.ganglia.port.internal.state.ColdStorage;

/** File-based L2 cold storage adapter. Stores fact details as files under a base directory. */
public class FileColdStorage implements ColdStorage {

  private final Vertx vertx;
  private final String basePath;

  public FileColdStorage(Vertx vertx, String basePath) {
    this.vertx = vertx;
    this.basePath = basePath;
  }

  @Override
  public Future<Void> write(String detailRef, String content) {
    Path filePath = Path.of(basePath, detailRef);
    Path parentDir = filePath.getParent();

    return vertx
        .fileSystem()
        .mkdirs(parentDir.toString())
        .compose(v -> vertx.fileSystem().writeFile(filePath.toString(), Buffer.buffer(content)));
  }

  @Override
  public Future<String> read(String detailRef) {
    Path filePath = Path.of(basePath, detailRef);
    return vertx
        .fileSystem()
        .exists(filePath.toString())
        .compose(
            exists -> {
              if (!exists) {
                return Future.succeededFuture(null);
              }
              return vertx.fileSystem().readFile(filePath.toString()).map(Buffer::toString);
            });
  }
}
