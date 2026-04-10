package work.ganglia.infrastructure.internal.memory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.TimelineLedger;
import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.TimelineEvent;

/**
 * SQLite-backed {@link TimelineLedger} with full read support (unlike the FS write-only version).
 */
public class SqliteTimelineLedger implements TimelineLedger {

  private static final Logger logger = LoggerFactory.getLogger(SqliteTimelineLedger.class);

  private final SqliteConnectionManager cm;

  public SqliteTimelineLedger(SqliteConnectionManager cm) {
    this.cm = cm;
  }

  @Override
  public Future<Void> appendEvent(TimelineEvent event) {
    return cm.executeBlockingVoid(
        conn -> {
          String sql =
              "INSERT INTO timeline_events (event_id, description, category, event_time, affected_files)"
                  + " VALUES (?, ?, ?, ?, ?)";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, event.eventId());
            ps.setString(2, event.description());
            ps.setString(3, event.category() != null ? event.category().name() : null);
            ps.setString(4, event.timestamp().toString());
            ps.setString(5, SqliteMemoryStore.serializeStringList(event.affectedFiles()));
            ps.executeUpdate();
          }
          logger.debug("Appended timeline event: {}", event.eventId());
        });
  }

  @Override
  public Future<List<TimelineEvent>> getRecentEvents(int limit) {
    return cm.executeBlocking(
        conn -> {
          List<TimelineEvent> events = new ArrayList<>();
          String sql = "SELECT * FROM timeline_events ORDER BY event_time DESC LIMIT ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
              while (rs.next()) {
                MemoryCategory category;
                try {
                  category = MemoryCategory.valueOf(rs.getString("category"));
                } catch (Exception e) {
                  category = MemoryCategory.UNKNOWN;
                }
                events.add(
                    new TimelineEvent(
                        rs.getString("event_id"),
                        rs.getString("description"),
                        category,
                        Instant.parse(rs.getString("event_time")),
                        SqliteMemoryStore.deserializeStringList(rs.getString("affected_files"))));
              }
            }
          }
          return events;
        });
  }
}
