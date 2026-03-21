package work.ganglia.infrastructure.internal.state;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.json.Json;
import java.util.Collections;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.StateEngine;
import work.ganglia.util.Constants;

public class FileStateEngine implements StateEngine {
  private static final Logger logger = LoggerFactory.getLogger(FileStateEngine.class);
  private static final String STATE_DIR = Constants.DIR_STATE;
  private final Vertx vertx;

  public FileStateEngine(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public Future<SessionContext> loadSession(String sessionId) {
    String filename = getFileName(sessionId);
    return vertx
        .fileSystem()
        .readFile(filename)
        .map(buffer -> Json.decodeValue(buffer, SessionContext.class));
  }

  @Override
  public Future<Void> saveSession(SessionContext context) {
    String filename = getFileName(context.sessionId());
    String tempFile = filename + ".tmp";

    // Asynchronous encoding and writing to temp file, then atomic move to replace existing
    return work.ganglia.util.FileSystemUtil.ensureDirectoryExists(vertx, STATE_DIR)
        .compose(
            v ->
                vertx.fileSystem().writeFile(tempFile, Buffer.buffer(Json.encodePrettily(context))))
        .compose(
            v ->
                vertx
                    .fileSystem()
                    .move(tempFile, filename, new CopyOptions().setReplaceExisting(true)));
  }

  @Override
  public SessionContext createSession() {
    return createSession(UUID.randomUUID().toString());
  }

  private SessionContext createSession(String sessionId) {
    return new SessionContext(
        sessionId,
        Collections.emptyList(),
        null,
        Collections.emptyMap(),
        Collections.emptyList(),
        null // ModelOptions (will be set by Loop or default)
        );
  }

  private String getFileName(String sessionId) {
    return STATE_DIR + "/session_" + sessionId + ".json";
  }
}
