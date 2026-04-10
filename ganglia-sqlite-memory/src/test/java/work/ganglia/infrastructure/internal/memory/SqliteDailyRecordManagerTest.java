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
class SqliteDailyRecordManagerTest {

  private SqliteConnectionManager cm;
  private SqliteDailyRecordManager manager;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    cm = new SqliteConnectionManager(vertx);
    cm.initSchema()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                manager = new SqliteDailyRecordManager(cm);
                ctx.completeNow();
              } else {
                ctx.failNow(ar.cause());
              }
            });
  }

  @AfterEach
  void tearDown() {
    cm.close();
  }

  @Test
  void recordInsertsRow(VertxTestContext ctx) {
    manager
        .record("s1", "Fix bug", "Fixed the auth bug")
        .onComplete(
            ctx.succeeding(
                v -> {
                  try {
                    Statement stmt = cm.getConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM daily_records");
                    rs.next();
                    assertEquals(1, rs.getInt(1));

                    rs =
                        stmt.executeQuery(
                            "SELECT session_id, goal, accomplishments FROM daily_records");
                    rs.next();
                    assertEquals("s1", rs.getString("session_id"));
                    assertEquals("Fix bug", rs.getString("goal"));
                    assertEquals("Fixed the auth bug", rs.getString("accomplishments"));
                    ctx.completeNow();
                  } catch (Exception e) {
                    ctx.failNow(e);
                  }
                }));
  }

  @Test
  void multipleRecordsSameDay(VertxTestContext ctx) {
    manager
        .record("s1", "Goal 1", "Done 1")
        .compose(v -> manager.record("s2", "Goal 2", "Done 2"))
        .onComplete(
            ctx.succeeding(
                v -> {
                  try {
                    Statement stmt = cm.getConnection().createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM daily_records");
                    rs.next();
                    assertEquals(2, rs.getInt(1));
                    ctx.completeNow();
                  } catch (Exception e) {
                    ctx.failNow(e);
                  }
                }));
  }
}
