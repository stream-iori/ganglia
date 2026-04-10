package work.ganglia.infrastructure.internal.memory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.SessionStore;
import work.ganglia.port.internal.memory.model.SessionRecord;
import work.ganglia.port.internal.memory.model.SessionSummary;

/** SQLite-backed {@link SessionStore} with FTS5 full-text search on goal and summary. */
public class SqliteSessionStore implements SessionStore {

  private static final Logger logger = LoggerFactory.getLogger(SqliteSessionStore.class);
  private static final int SNIPPET_MAX_LENGTH = 120;

  private final SqliteConnectionManager cm;

  public SqliteSessionStore(SqliteConnectionManager cm) {
    this.cm = cm;
  }

  @Override
  public Future<Void> saveSession(SessionRecord record) {
    return cm.executeBlockingVoid(
        conn -> {
          // DELETE + INSERT to properly trigger FTS sync (INSERT OR REPLACE
          // does not fire the DELETE trigger needed for FTS content sync)
          try (PreparedStatement del =
              conn.prepareStatement("DELETE FROM sessions WHERE session_id = ?")) {
            del.setString(1, record.sessionId());
            del.executeUpdate();
          }
          String sql =
              "INSERT INTO sessions"
                  + " (session_id, goal, summary, turn_count, tool_call_count, start_time,"
                  + " end_time)"
                  + " VALUES (?, ?, ?, ?, ?, ?, ?)";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, record.sessionId());
            ps.setString(2, record.goal());
            ps.setString(3, record.summary());
            ps.setInt(4, record.turnCount());
            ps.setInt(5, record.toolCallCount());
            ps.setString(6, record.startTime() != null ? record.startTime().toString() : null);
            ps.setString(7, record.endTime() != null ? record.endTime().toString() : null);
            ps.executeUpdate();
          }
          logger.debug("Saved session: {}", record.sessionId());
        });
  }

  @Override
  public Future<List<SessionSummary>> searchSessions(String query, int limit) {
    return cm.executeBlocking(
        conn -> {
          List<SessionSummary> results = new ArrayList<>();
          boolean hasFts = query != null && !query.isBlank();

          String sql;
          if (hasFts) {
            sql =
                "SELECT s.session_id, s.goal, s.summary, s.start_time"
                    + " FROM sessions s"
                    + " JOIN sessions_fts f ON s.rowid = f.rowid"
                    + " WHERE sessions_fts MATCH ?"
                    + " ORDER BY s.start_time DESC LIMIT ?";
          } else {
            sql =
                "SELECT session_id, goal, summary, start_time FROM sessions"
                    + " ORDER BY start_time DESC LIMIT ?";
          }

          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int paramIdx = 1;
            if (hasFts) {
              ps.setString(paramIdx++, escapeFts(query));
            }
            ps.setInt(paramIdx, limit > 0 ? limit : 10);

            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                String goal = rs.getString("goal");
                String summary = rs.getString("summary");
                String snippet = buildSnippet(goal, summary);
                String startTimeStr = rs.getString("start_time");
                Instant startTime = startTimeStr != null ? Instant.parse(startTimeStr) : null;
                results.add(
                    new SessionSummary(rs.getString("session_id"), goal, snippet, startTime));
              }
            }
          }
          return results;
        });
  }

  @Override
  public Future<SessionRecord> getSession(String sessionId) {
    return cm.executeBlocking(
        conn -> {
          String sql = "SELECT * FROM sessions WHERE session_id = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return mapRecord(rs);
              }
            }
          }
          throw new RuntimeException("Session not found: " + sessionId);
        });
  }

  private SessionRecord mapRecord(ResultSet rs) throws java.sql.SQLException {
    String startTimeStr = rs.getString("start_time");
    String endTimeStr = rs.getString("end_time");
    return new SessionRecord(
        rs.getString("session_id"),
        rs.getString("goal"),
        rs.getString("summary"),
        rs.getInt("turn_count"),
        rs.getInt("tool_call_count"),
        startTimeStr != null ? Instant.parse(startTimeStr) : null,
        endTimeStr != null ? Instant.parse(endTimeStr) : null);
  }

  /** Escapes a search query for FTS5 by wrapping each word in double quotes. */
  private String escapeFts(String keyword) {
    String[] words = keyword.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      if (i > 0) {
        sb.append(" ");
      }
      sb.append("\"").append(words[i].replace("\"", "\"\"")).append("\"");
    }
    return sb.toString();
  }

  private String buildSnippet(String goal, String summary) {
    String source = goal != null ? goal : (summary != null ? summary : "");
    if (source.length() > SNIPPET_MAX_LENGTH) {
      return source.substring(0, SNIPPET_MAX_LENGTH) + "...";
    }
    return source;
  }
}
