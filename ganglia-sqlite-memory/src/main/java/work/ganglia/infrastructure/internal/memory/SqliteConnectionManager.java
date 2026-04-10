package work.ganglia.infrastructure.internal.memory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

/**
 * Manages a single SQLite connection with WAL mode, schema initialization, and migration tracking.
 */
public class SqliteConnectionManager {

  private static final Logger logger = LoggerFactory.getLogger(SqliteConnectionManager.class);
  private static final String MIGRATION_PREFIX = "db/V";
  private static final String MIGRATION_SUFFIX = "__";

  private final Vertx vertx;
  private final String dbPath;
  private Connection connection;

  public SqliteConnectionManager(Vertx vertx, String dbPath) {
    this.vertx = vertx;
    this.dbPath = dbPath;
  }

  /** Creates a manager backed by an in-memory database (for testing). */
  public SqliteConnectionManager(Vertx vertx) {
    this(vertx, ":memory:");
  }

  public synchronized Connection getConnection() throws SQLException {
    if (connection == null || connection.isClosed()) {
      connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
      try (Statement stmt = connection.createStatement()) {
        stmt.execute("PRAGMA journal_mode=WAL");
        stmt.execute("PRAGMA foreign_keys=ON");
        stmt.execute("PRAGMA busy_timeout=5000");
      }
    }
    return connection;
  }

  /** Initializes the schema by executing pending migration scripts. */
  public Future<Void> initSchema() {
    return vertx.executeBlocking(
        () -> {
          ensureParentDirectory();
          Connection conn = getConnection();

          // Ensure schema_version table exists (bootstrap)
          ensureSchemaVersionTable(conn);

          int currentVersion = getCurrentVersion(conn);
          List<Migration> pending = discoverMigrations(currentVersion);

          for (Migration migration : pending) {
            logger.info("Applying migration V{}: {}", migration.version, migration.description);
            try (Statement stmt = conn.createStatement()) {
              for (String ddl : splitStatements(migration.sql)) {
                stmt.execute(ddl);
              }
            }
            recordMigration(conn, migration.version, migration.description);
          }

          if (pending.isEmpty()) {
            logger.debug("SQLite schema up to date (v{}) at {}", currentVersion, dbPath);
          } else {
            logger.info(
                "SQLite schema migrated to v{} at {}",
                pending.get(pending.size() - 1).version,
                dbPath);
          }
          return null;
        });
  }

  /** Returns the current schema version (0 if no migrations applied). */
  public Future<Integer> getSchemaVersion() {
    return executeBlocking(
        conn -> {
          ensureSchemaVersionTable(conn);
          return getCurrentVersion(conn);
        });
  }

  private void ensureSchemaVersionTable(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
      stmt.execute(
          "CREATE TABLE IF NOT EXISTS schema_version ("
              + "version INTEGER PRIMARY KEY, "
              + "description TEXT NOT NULL, "
              + "applied_at TEXT NOT NULL)");
    }
  }

  private int getCurrentVersion(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
      if (rs.next()) {
        return rs.getInt(1); // returns 0 for NULL
      }
    }
    return 0;
  }

  private void recordMigration(Connection conn, int version, String description)
      throws SQLException {
    String sql = "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, version);
      ps.setString(2, description);
      ps.setString(3, Instant.now().toString());
      ps.executeUpdate();
    }
  }

  /**
   * Discovers migration scripts on the classpath with version > currentVersion. Scripts are named
   * {@code db/V{n}__{description}.sql}.
   */
  private List<Migration> discoverMigrations(int currentVersion) {
    List<Migration> migrations = new ArrayList<>();
    for (int v = currentVersion + 1; ; v++) {
      String resource = findMigrationResource(v);
      if (resource == null) {
        break;
      }
      String sql = loadResource(resource);
      String description = extractDescription(resource);
      migrations.add(new Migration(v, description, sql));
    }
    return migrations;
  }

  private String findMigrationResource(int version) {
    // Try a few naming conventions: V1__init_schema.sql, V2__add_fts.sql, etc.
    String prefix = MIGRATION_PREFIX + version + MIGRATION_SUFFIX;
    // Scan known resource by probing with ClassLoader
    // Since we can't list classpath resources portably, try loading with pattern
    String[] candidates = listMigrationCandidates(version);
    for (String candidate : candidates) {
      if (getClass().getClassLoader().getResource(candidate) != null) {
        return candidate;
      }
    }
    return null;
  }

  private String[] listMigrationCandidates(int version) {
    // To support arbitrary descriptions, we use a registry approach:
    // Register known migration files here. For extensibility, new migrations
    // just need to be added to this list.
    return switch (version) {
      case 1 -> new String[] {"db/V1__init_schema.sql"};
      default -> new String[] {};
    };
  }

  private String extractDescription(String resource) {
    // "db/V1__init_schema.sql" → "init_schema"
    String name = resource.substring(resource.lastIndexOf('/') + 1);
    int start = name.indexOf("__");
    int end = name.lastIndexOf('.');
    if (start >= 0 && end > start) {
      return name.substring(start + 2, end).replace('_', ' ');
    }
    return name;
  }

  /**
   * Splits a SQL script into individual statements, correctly handling BEGIN...END blocks (e.g.
   * triggers) that contain semicolons.
   */
  static List<String> splitStatements(String sql) {
    List<String> statements = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;

    for (String line : sql.split("\n")) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("--")) {
        continue;
      }

      current.append(line).append("\n");
      String upper = trimmed.toUpperCase();

      // Detect BEGIN anywhere in the line (e.g. "... ON table BEGIN")
      if (upper.contains(" BEGIN") || upper.equals("BEGIN")) {
        depth++;
      }

      // Detect END; at the start of a trimmed line (trigger block end)
      if (depth > 0 && upper.startsWith("END;")) {
        depth--;
        if (depth == 0) {
          statements.add(current.toString().trim());
          current.setLength(0);
        }
      } else if (depth == 0 && trimmed.endsWith(";")) {
        String stmt = current.toString().trim();
        // Remove trailing semicolon for JDBC
        if (stmt.endsWith(";")) {
          stmt = stmt.substring(0, stmt.length() - 1).trim();
        }
        if (!stmt.isEmpty()) {
          statements.add(stmt);
        }
        current.setLength(0);
      }
    }

    String remaining = current.toString().trim();
    if (!remaining.isEmpty()) {
      if (remaining.endsWith(";")) {
        remaining = remaining.substring(0, remaining.length() - 1).trim();
      }
      if (!remaining.isEmpty()) {
        statements.add(remaining);
      }
    }

    return statements;
  }

  /** Closes the underlying connection. */
  public synchronized void close() {
    if (connection != null) {
      try {
        connection.close();
      } catch (SQLException e) {
        logger.warn("Error closing SQLite connection", e);
      }
      connection = null;
    }
  }

  /** Wraps a blocking JDBC operation in a Vert.x executeBlocking call. */
  <T> Future<T> executeBlocking(SqlCallable<T> callable) {
    return vertx.executeBlocking(
        () -> {
          try {
            return callable.call(getConnection());
          } catch (SQLException e) {
            throw new RuntimeException("SQLite operation failed", e);
          }
        });
  }

  /** Wraps a blocking JDBC operation that returns nothing. */
  Future<Void> executeBlockingVoid(SqlRunnable runnable) {
    return vertx.executeBlocking(
        () -> {
          try {
            runnable.run(getConnection());
          } catch (SQLException e) {
            throw new RuntimeException("SQLite operation failed", e);
          }
          return null;
        });
  }

  private void ensureParentDirectory() {
    if (":memory:".equals(dbPath)) {
      return;
    }
    Path parent = Paths.get(dbPath).getParent();
    if (parent != null) {
      try {
        Files.createDirectories(parent);
      } catch (IOException e) {
        logger.warn("Could not create parent directory for {}", dbPath, e);
      }
    }
  }

  private String loadResource(String resource) {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
      if (is == null) {
        throw new IllegalStateException("Resource not found: " + resource);
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        return reader.lines().collect(Collectors.joining("\n"));
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read resource: " + resource, e);
    }
  }

  @FunctionalInterface
  interface SqlCallable<T> {
    T call(Connection conn) throws SQLException;
  }

  @FunctionalInterface
  interface SqlRunnable {
    void run(Connection conn) throws SQLException;
  }

  private record Migration(int version, String description, String sql) {}
}
