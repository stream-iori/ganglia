package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.model.SessionRecord;

@ExtendWith(VertxExtension.class)
class FileSystemSessionStoreTest {

  private FileSystemSessionStore store;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.store = new FileSystemSessionStore(vertx, tempDir.toString());
  }

  @Test
  void saveAndGetSession(Vertx vertx, VertxTestContext ctx) {
    Instant start = Instant.parse("2026-04-10T06:00:00Z");
    Instant end = Instant.parse("2026-04-10T06:30:00Z");
    SessionRecord record =
        new SessionRecord("s1", "Fix auth bug", "Fixed JWT validation", 5, 12, start, end);

    store
        .saveSession(record)
        .compose(v -> store.getSession("s1"))
        .onComplete(
            ctx.succeeding(
                fetched ->
                    ctx.verify(
                        () -> {
                          assertEquals("s1", fetched.sessionId());
                          assertEquals("Fix auth bug", fetched.goal());
                          assertEquals("Fixed JWT validation", fetched.summary());
                          assertEquals(5, fetched.turnCount());
                          assertEquals(12, fetched.toolCallCount());
                          assertEquals(start, fetched.startTime());
                          assertEquals(end, fetched.endTime());
                          ctx.completeNow();
                        })));
  }

  @Test
  void getSession_notFound_failsFuture(Vertx vertx, VertxTestContext ctx) {
    store
        .getSession("nonexistent")
        .onComplete(
            ctx.failing(
                err ->
                    ctx.verify(
                        () -> {
                          assertTrue(err.getMessage().contains("not found"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_matchesGoal(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Fix auth bug", "Fixed JWT", 3, 5, now, now);
    SessionRecord r2 =
        new SessionRecord("s2", "Add logging", "Added structured logs", 4, 8, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.searchSessions("auth", 10))
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertEquals(1, results.size());
                          assertEquals("s1", results.get(0).sessionId());
                          assertEquals("Fix auth bug", results.get(0).goal());
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_matchesSummary(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 =
        new SessionRecord("s1", "Fix bug", "Fixed JWT validation logic", 3, 5, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.searchSessions("JWT", 10))
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertEquals(1, results.size());
                          assertEquals("s1", results.get(0).sessionId());
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_caseInsensitive(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Fix Authentication Bug", "done", 3, 5, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.searchSessions("authentication", 10))
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertEquals(1, results.size());
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_respectsLimit(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Task one", "done", 1, 1, now, now);
    SessionRecord r2 = new SessionRecord("s2", "Task two", "done", 2, 2, now, now);
    SessionRecord r3 = new SessionRecord("s3", "Task three", "done", 3, 3, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.saveSession(r3))
        .compose(v -> store.searchSessions("Task", 2))
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertEquals(2, results.size());
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_noMatch_returnsEmpty(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Fix bug", "done", 1, 1, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.searchSessions("deployment", 10))
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertTrue(results.isEmpty());
                          ctx.completeNow();
                        })));
  }

  @Test
  void searchSessions_emptyStore_returnsEmpty(Vertx vertx, VertxTestContext ctx) {
    store
        .searchSessions("anything", 10)
        .onComplete(
            ctx.succeeding(
                results ->
                    ctx.verify(
                        () -> {
                          assertTrue(results.isEmpty());
                          ctx.completeNow();
                        })));
  }

  @Test
  void saveSession_overwritesExisting(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Original goal", "v1", 1, 1, now, now);
    SessionRecord r2 = new SessionRecord("s1", "Updated goal", "v2", 2, 3, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.getSession("s1"))
        .onComplete(
            ctx.succeeding(
                fetched ->
                    ctx.verify(
                        () -> {
                          assertEquals("Updated goal", fetched.goal());
                          assertEquals("v2", fetched.summary());
                          ctx.completeNow();
                        })));
  }

  @Test
  void multipleSessions_allPersisted(Vertx vertx, VertxTestContext ctx) {
    Instant now = Instant.now();
    SessionRecord r1 = new SessionRecord("s1", "Task A", "done A", 1, 1, now, now);
    SessionRecord r2 = new SessionRecord("s2", "Task B", "done B", 2, 2, now, now);

    store
        .saveSession(r1)
        .compose(v -> store.saveSession(r2))
        .compose(v -> store.getSession("s1"))
        .compose(
            s1 -> {
              assertEquals("Task A", s1.goal());
              return store.getSession("s2");
            })
        .onComplete(
            ctx.succeeding(
                s2 ->
                    ctx.verify(
                        () -> {
                          assertEquals("Task B", s2.goal());
                          ctx.completeNow();
                        })));
  }
}
