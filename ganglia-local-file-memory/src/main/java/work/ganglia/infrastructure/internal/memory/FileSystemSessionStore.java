package work.ganglia.infrastructure.internal.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.memory.model.SessionSummary;
import work.ganglia.util.FileSystemUtil;

/**
 * File-system-based implementation of {@link SessionStore}. Stores session records as JSON in a
 * single sessions.json file within the memory directory.
 */
public class FileSystemSessionStore implements SessionStore {
  private static final Logger logger = LoggerFactory.getLogger(FileSystemSessionStore.class);
  private static final String SESSIONS_FILE = "sessions.json";

  private final Vertx vertx;
  private final String basePath;
  private final String filePath;

  public FileSystemSessionStore(Vertx vertx, String basePath) {
    this.vertx = vertx;
    this.basePath = basePath;
    this.filePath = basePath + "/" + SESSIONS_FILE;
  }

  @Override
  public Future<Void> saveSession(SessionRecord record) {
    return ensureFile()
        .compose(v -> vertx.fileSystem().readFile(filePath))
        .compose(
            buffer -> {
              JsonArray sessions = new JsonArray(buffer.toString());
              // Remove existing record with same sessionId (overwrite)
              JsonArray updated = new JsonArray();
              for (int i = 0; i < sessions.size(); i++) {
                JsonObject obj = sessions.getJsonObject(i);
                if (!record.sessionId().equals(obj.getString("sessionId"))) {
                  updated.add(obj);
                }
              }
              updated.add(toJson(record));
              return vertx
                  .fileSystem()
                  .writeFile(filePath, Buffer.buffer(updated.encodePrettily()));
            })
        .onSuccess(v -> logger.debug("Session saved: {}", record.sessionId()))
        .onFailure(err -> logger.error("Failed to save session: {}", record.sessionId(), err));
  }

  @Override
  public Future<List<SessionSummary>> searchSessions(String query, int limit) {
    return ensureFile()
        .compose(v -> vertx.fileSystem().readFile(filePath))
        .map(
            buffer -> {
              JsonArray sessions = new JsonArray(buffer.toString());
              String lowerQuery = query.toLowerCase(Locale.ROOT);
              List<SessionSummary> results = new ArrayList<>();

              // Iterate in reverse order (most recent first)
              for (int i = sessions.size() - 1; i >= 0 && results.size() < limit; i--) {
                JsonObject obj = sessions.getJsonObject(i);
                String goal = obj.getString("goal", "");
                String summary = obj.getString("summary", "");

                if (goal.toLowerCase(Locale.ROOT).contains(lowerQuery)
                    || summary.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                  String matchSnippet = buildSnippet(goal, summary, lowerQuery);
                  results.add(
                      new SessionSummary(
                          obj.getString("sessionId"),
                          goal,
                          matchSnippet,
                          Instant.parse(obj.getString("startTime"))));
                }
              }
              return results;
            });
  }

  @Override
  public Future<SessionRecord> getSession(String sessionId) {
    return ensureFile()
        .compose(v -> vertx.fileSystem().readFile(filePath))
        .compose(
            buffer -> {
              JsonArray sessions = new JsonArray(buffer.toString());
              for (int i = 0; i < sessions.size(); i++) {
                JsonObject obj = sessions.getJsonObject(i);
                if (sessionId.equals(obj.getString("sessionId"))) {
                  return Future.succeededFuture(fromJson(obj));
                }
              }
              return Future.failedFuture("Session not found: " + sessionId);
            });
  }

  private Future<Void> ensureFile() {
    return FileSystemUtil.ensureDirectoryExists(vertx, basePath)
        .compose(
            v ->
                vertx
                    .fileSystem()
                    .exists(filePath)
                    .compose(
                        exists -> {
                          if (exists) {
                            return Future.succeededFuture();
                          }
                          return vertx
                              .fileSystem()
                              .writeFile(filePath, Buffer.buffer("[]"))
                              .mapEmpty();
                        }));
  }

  private static JsonObject toJson(SessionRecord record) {
    return new JsonObject()
        .put("sessionId", record.sessionId())
        .put("goal", record.goal())
        .put("summary", record.summary())
        .put("turnCount", record.turnCount())
        .put("toolCallCount", record.toolCallCount())
        .put("startTime", record.startTime().toString())
        .put("endTime", record.endTime().toString());
  }

  private static SessionRecord fromJson(JsonObject obj) {
    return new SessionRecord(
        obj.getString("sessionId"),
        obj.getString("goal"),
        obj.getString("summary"),
        obj.getInteger("turnCount", 0),
        obj.getInteger("toolCallCount", 0),
        Instant.parse(obj.getString("startTime")),
        Instant.parse(obj.getString("endTime")));
  }

  private static String buildSnippet(String goal, String summary, String lowerQuery) {
    // Return whichever field matched, truncated
    if (goal.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
      return truncate(goal, 120);
    }
    return truncate(summary, 120);
  }

  private static String truncate(String text, int maxLen) {
    if (text.length() <= maxLen) {
      return text;
    }
    return text.substring(0, maxLen) + "...";
  }
}
