package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SqliteConnectionManagerTest {

  private SqliteConnectionManager cm;

  @BeforeEach
  void setUp(Vertx vertx) {
    cm = new SqliteConnectionManager(vertx);
  }

  @AfterEach
  void tearDown() {
    cm.close();
  }

  @Test
  void initSchemaRecordsVersion(Vertx vertx, VertxTestContext ctx) {
    cm.initSchema()
        .compose(v -> cm.getSchemaVersion())
        .onComplete(
            ctx.succeeding(
                version -> {
                  assertEquals(1, version, "Schema version should be 1 after V1 migration");
                  ctx.completeNow();
                }));
  }

  @Test
  void initSchemaIsIdempotent(Vertx vertx, VertxTestContext ctx) {
    cm.initSchema()
        .compose(v -> cm.initSchema())
        .compose(v -> cm.getSchemaVersion())
        .onComplete(
            ctx.succeeding(
                version -> {
                  assertEquals(1, version, "Version should still be 1 after double init");
                  ctx.completeNow();
                }));
  }

  @Test
  void schemaVersionTableCreatedByInit(Vertx vertx, VertxTestContext ctx) {
    cm.initSchema()
        .compose(
            v ->
                cm.executeBlocking(
                    conn -> {
                      try (Statement stmt = conn.createStatement();
                          ResultSet rs =
                              stmt.executeQuery(
                                  "SELECT version, description FROM schema_version"
                                      + " ORDER BY version")) {
                        assertTrue(rs.next(), "Should have at least one version row");
                        assertEquals(1, rs.getInt("version"));
                        assertNotNull(rs.getString("description"));
                        return null;
                      }
                    }))
        .onComplete(ctx.succeeding(r -> ctx.completeNow()));
  }

  @Test
  void splitStatementsHandlesTriggers() {
    String sql =
        "CREATE TABLE t (id INT);\n"
            + "CREATE TRIGGER tr AFTER INSERT ON t BEGIN\n"
            + "    INSERT INTO t2 VALUES (1);\n"
            + "END;\n"
            + "CREATE TABLE t2 (id INT);\n";
    var stmts = SqliteConnectionManager.splitStatements(sql);
    assertEquals(3, stmts.size());
    assertTrue(stmts.get(1).contains("TRIGGER"));
    assertTrue(stmts.get(1).contains("END;"));
  }
}
