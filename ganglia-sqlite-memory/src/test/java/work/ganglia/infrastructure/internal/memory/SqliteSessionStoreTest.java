package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.model.SessionRecord;

@ExtendWith(VertxExtension.class)
class SqliteSessionStoreTest {

  private SqliteConnectionManager cm;
  private SqliteSessionStore store;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    cm = new SqliteConnectionManager(vertx);
    cm.initSchema()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                store = new SqliteSessionStore(cm);
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
  void saveAndGetSession(VertxTestContext ctx) {
    SessionRecord record =
        new SessionRecord("s1", "Fix auth", "Fixed JWT bug", 5, 12, Instant.now(), Instant.now());

    store
        .saveSession(record)
        .compose(v -> store.getSession("s1"))
        .onComplete(
            ctx.succeeding(
                r -> {
                  assertEquals("s1", r.sessionId());
                  assertEquals("Fix auth", r.goal());
                  assertEquals(5, r.turnCount());
                  assertEquals(12, r.toolCallCount());
                  ctx.completeNow();
                }));
  }

  @Test
  void getSessionNotFound(VertxTestContext ctx) {
    store
        .getSession("nonexistent")
        .onComplete(
            ar -> {
              assertTrue(ar.failed());
              assertTrue(ar.cause().getMessage().contains("not found"));
              ctx.completeNow();
            });
  }

  @Test
  void saveOverwritesExisting(VertxTestContext ctx) {
    SessionRecord v1 =
        new SessionRecord("s1", "Goal v1", "Summary v1", 1, 1, Instant.now(), Instant.now());
    SessionRecord v2 =
        new SessionRecord("s1", "Goal v2", "Summary v2", 10, 20, Instant.now(), Instant.now());

    store
        .saveSession(v1)
        .compose(v -> store.saveSession(v2))
        .compose(v -> store.getSession("s1"))
        .onComplete(
            ctx.succeeding(
                r -> {
                  assertEquals("Goal v2", r.goal());
                  assertEquals(10, r.turnCount());
                  ctx.completeNow();
                }));
  }

  @Test
  void searchSessions(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
    SessionRecord r1 = new SessionRecord("s1", "Fix auth bug", "Fixed JWT", 5, 10, t1, t1);
    SessionRecord r2 = new SessionRecord("s2", "Add caching layer", "Redis setup", 3, 8, t2, t2);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.searchSessions("auth", 10))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  assertEquals("s1", results.get(0).sessionId());
                  ctx.completeNow();
                }));
  }

  @Test
  void searchSessionsReturnsNewestFirst(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
    SessionRecord r1 = new SessionRecord("s1", "Task A", "Sum A", 1, 1, t1, t1);
    SessionRecord r2 = new SessionRecord("s2", "Task B", "Sum B", 2, 2, t2, t2);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.searchSessions("Task", 10))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(2, results.size());
                  assertEquals("s2", results.get(0).sessionId());
                  ctx.completeNow();
                }));
  }

  @Test
  void searchWithNullQueryReturnsAll(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    SessionRecord r1 = new SessionRecord("s1", "Fix auth", "JWT", 1, 1, t1, t1);

    store
        .saveSession(r1)
        .compose(v -> store.searchSessions(null, 10))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  ctx.completeNow();
                }));
  }

  @Test
  void searchWithBlankQueryReturnsAll(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    SessionRecord r1 = new SessionRecord("s1", "Fix auth", "JWT", 1, 1, t1, t1);

    store
        .saveSession(r1)
        .compose(v -> store.searchSessions("  ", 10))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  ctx.completeNow();
                }));
  }

  @Test
  void ftsSearchMatchesOnSummary(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
    SessionRecord r1 =
        new SessionRecord("s1", "Deploy service", "Updated Kubernetes manifests", 5, 10, t1, t1);
    SessionRecord r2 =
        new SessionRecord("s2", "Fix auth", "Updated JWT token handling", 3, 8, t2, t2);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.searchSessions("Kubernetes", 10))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  assertEquals("s1", results.get(0).sessionId());
                  ctx.completeNow();
                }));
  }

  @Test
  void updateSessionKeepsFtsSynced(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    SessionRecord v1 = new SessionRecord("s1", "Fix auth bug", "JWT", 1, 1, t1, t1);
    SessionRecord v2 = new SessionRecord("s1", "Deploy pipeline", "CI/CD", 5, 10, t1, t1);

    store
        .saveSession(v1)
        .compose(v -> store.saveSession(v2))
        // Old goal should no longer match
        .compose(v -> store.searchSessions("auth", 10))
        .compose(
            authResults -> {
              assertEquals(0, authResults.size(), "Old goal should not match after update");
              return store.searchSessions("pipeline", 10);
            })
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  assertEquals("s1", results.get(0).sessionId());
                  ctx.completeNow();
                }));
  }
}
