package me.stream.ganglia.core.state;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.model.ToDoList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

public class FileStateEngine implements StateEngine {
    private static final Logger logger = LoggerFactory.getLogger(FileStateEngine.class);
    private static final String STATE_DIR = ".ganglia/state";
    private final Vertx vertx;

    public FileStateEngine(Vertx vertx) {
        this.vertx = vertx;
        // Ensure state directory exists
        File dir = new File(STATE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public Future<SessionContext> loadSession(String sessionId) {
        String filename = getFileName(sessionId);
        return vertx.fileSystem().readFile(filename)
                .map(buffer -> Json.decodeValue(buffer, SessionContext.class))
                .recover(err -> {
                    logger.warn("Failed to load session {}, creating new one. Error: {}", sessionId, err.getMessage());
                    return Future.succeededFuture(createSession(sessionId));
                });
    }

    @Override
    public Future<Void> saveSession(SessionContext context) {
        String filename = getFileName(context.sessionId());
        return vertx.executeBlocking(() -> {
            try {
                // Atomic write pattern: write to temp, then rename
                String tempFile = filename + ".tmp";
                String json = Json.encodePrettily(context); // Pretty print for debuggability
                vertx.fileSystem().writeFileBlocking(tempFile, Buffer.buffer(json));
                vertx.fileSystem().moveBlocking(tempFile, filename);
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to save session state", e);
            }
        });
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
                null, // ModelOptions (will be set by Loop or default)
                ToDoList.empty()
        );
    }

    private String getFileName(String sessionId) {
        return STATE_DIR + "/session_" + sessionId + ".json";
    }
}
