package work.ganglia.infrastructure.internal.memory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.port.internal.memory.LongTermMemory;

/** SQLite-backed {@link LongTermMemory}. Topics are stored as key-value rows. */
public class SqliteLongTermMemory implements LongTermMemory {

  private static final Logger logger = LoggerFactory.getLogger(SqliteLongTermMemory.class);

  private static final String DEFAULT_PROJECT_TEMPLATE =
      "# Project Memory\n\n## Project Conventions\n\n## Architecture Decisions\n\n## Lessons Learned\n";
  private static final String USER_PROFILE_TEMPLATE =
      "# User Profile\n\n## Communication Style\n\n## Technical Background\n\n## Preferences\n";

  private final SqliteConnectionManager cm;

  public SqliteLongTermMemory(SqliteConnectionManager cm) {
    this.cm = cm;
  }

  @Override
  public Future<Void> ensureInitialized(String topic) {
    return cm.executeBlockingVoid(
        conn -> {
          String sql = "INSERT OR IGNORE INTO long_term_memory (topic, content) VALUES (?, ?)";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic);
            ps.setString(2, templateFor(topic));
            ps.executeUpdate();
          }
          logger.debug("Ensured topic initialized: {}", topic);
        });
  }

  @Override
  public Future<String> read(String topic) {
    return cm.executeBlocking(
        conn -> {
          String sql = "SELECT content FROM long_term_memory WHERE topic = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return rs.getString("content");
              }
            }
          }
          return "";
        });
  }

  @Override
  public Future<Void> append(String topic, String content) {
    return cm.executeBlockingVoid(
        conn -> {
          String sql = "UPDATE long_term_memory SET content = content || ? WHERE topic = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "\n" + content + "\n");
            ps.setString(2, topic);
            ps.executeUpdate();
          }
          logger.debug("Appended to topic: {}", topic);
        });
  }

  @Override
  public Future<Integer> getSize(String topic) {
    return cm.executeBlocking(
        conn -> {
          String sql = "SELECT LENGTH(content) AS size FROM long_term_memory WHERE topic = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, topic);
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next()) {
                return rs.getInt("size");
              }
            }
          }
          return 0;
        });
  }

  @Override
  public Future<Void> replace(String topic, String content) {
    return cm.executeBlockingVoid(
        conn -> {
          String sql = "UPDATE long_term_memory SET content = ? WHERE topic = ?";
          try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, content);
            ps.setString(2, topic);
            ps.executeUpdate();
          }
          logger.debug("Replaced content for topic: {}", topic);
        });
  }

  private String templateFor(String topic) {
    if (USER_PROFILE_TOPIC.equals(topic)) {
      return USER_PROFILE_TEMPLATE;
    }
    return DEFAULT_PROJECT_TEMPLATE;
  }
}
