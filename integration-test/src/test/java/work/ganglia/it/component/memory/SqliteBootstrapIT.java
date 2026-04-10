package work.ganglia.it.component.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.it.support.SqliteModelIT;

/**
 * Verifies that Ganglia bootstraps correctly with the SQLite memory backend and creates the
 * expected database file.
 */
public class SqliteBootstrapIT extends SqliteModelIT {

  @Test
  void sqliteBackend_createsDatabaseFile(Vertx vertx, VertxTestContext testContext) {
    try {
      Path dbFile = tempDir.toRealPath().resolve(".ganglia/memory/memory.db");
      assertTrue(Files.exists(dbFile), "SQLite database file should exist at " + dbFile);
      assertTrue(Files.size(dbFile) > 0, "Database file should not be empty");
      testContext.completeNow();
    } catch (Exception e) {
      testContext.failNow(e);
    }
  }
}
