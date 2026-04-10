package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.TimelineEvent;

@ExtendWith(VertxExtension.class)
class SqliteTimelineLedgerTest {

  private SqliteConnectionManager cm;
  private SqliteTimelineLedger ledger;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    cm = new SqliteConnectionManager(vertx);
    cm.initSchema()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                ledger = new SqliteTimelineLedger(cm);
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
  void appendAndRetrieveEvent(VertxTestContext ctx) {
    TimelineEvent event =
        new TimelineEvent(
            "evt1",
            "Fixed auth bug",
            MemoryCategory.BUGFIX,
            Instant.now(),
            List.of("Auth.java", "AuthTest.java"));

    ledger
        .appendEvent(event)
        .compose(v -> ledger.getRecentEvents(10))
        .onComplete(
            ctx.succeeding(
                events -> {
                  assertEquals(1, events.size());
                  TimelineEvent e = events.get(0);
                  assertEquals("evt1", e.eventId());
                  assertEquals("Fixed auth bug", e.description());
                  assertEquals(MemoryCategory.BUGFIX, e.category());
                  assertEquals(2, e.affectedFiles().size());
                  ctx.completeNow();
                }));
  }

  @Test
  void getRecentEventsReturnsNewestFirst(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
    TimelineEvent e1 =
        new TimelineEvent("evt1", "Old event", MemoryCategory.OBSERVATION, t1, List.of());
    TimelineEvent e2 =
        new TimelineEvent("evt2", "New event", MemoryCategory.FEATURE, t2, List.of());

    ledger
        .appendEvent(e1)
        .compose(v -> ledger.appendEvent(e2))
        .compose(v -> ledger.getRecentEvents(10))
        .onComplete(
            ctx.succeeding(
                events -> {
                  assertEquals(2, events.size());
                  assertEquals("evt2", events.get(0).eventId());
                  assertEquals("evt1", events.get(1).eventId());
                  ctx.completeNow();
                }));
  }

  @Test
  void getRecentEventsRespectsLimit(VertxTestContext ctx) {
    TimelineEvent e1 =
        new TimelineEvent("evt1", "E1", MemoryCategory.OBSERVATION, Instant.now(), List.of());
    TimelineEvent e2 =
        new TimelineEvent("evt2", "E2", MemoryCategory.OBSERVATION, Instant.now(), List.of());

    ledger
        .appendEvent(e1)
        .compose(v -> ledger.appendEvent(e2))
        .compose(v -> ledger.getRecentEvents(1))
        .onComplete(
            ctx.succeeding(
                events -> {
                  assertEquals(1, events.size());
                  ctx.completeNow();
                }));
  }
}
