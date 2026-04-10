package work.ganglia.infrastructure.internal.memory;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.DailyRecordManager;

/** SQLite-backed {@link DailyRecordManager}. */
public class SqliteDailyRecordManager implements DailyRecordManager {

  private static final Logger logger = LoggerFactory.getLogger(SqliteDailyRecordManager.class);

  private final SqliteConnectionManager cm;

  public SqliteDailyRecordManager(SqliteConnectionManager cm) {
    this.cm = cm;
  }

  @Override
  public Future<Void> record(String sessionId, String goal, String accomplishments) {
    return cm.executeBlockingVoid(
        conn -> {
          String sql =
              "INSERT INTO daily_records (record_date, session_id, goal, accomplishments, created_at)"
                  + " VALUES (?, ?, ?, ?, ?)";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            ps.setString(2, sessionId);
            ps.setString(3, goal);
            ps.setString(4, accomplishments);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
          }
          logger.debug("Recorded daily entry for session: {}", sessionId);
        });
  }
}
