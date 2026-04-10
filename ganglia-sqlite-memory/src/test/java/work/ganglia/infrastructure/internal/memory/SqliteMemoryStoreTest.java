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
import work.ganglia.port.internal.memory.model.MemoryEntry;
import work.ganglia.port.internal.memory.model.MemoryQuery;
import work.ganglia.port.internal.memory.model.MemoryTag;

@ExtendWith(VertxExtension.class)
class SqliteMemoryStoreTest {

  private SqliteConnectionManager cm;
  private SqliteMemoryStore store;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    cm = new SqliteConnectionManager(vertx);
    cm.initSchema()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                store = new SqliteMemoryStore(cm);
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
  void storeAndRecall(VertxTestContext ctx) {
    MemoryEntry entry =
        new MemoryEntry(
            "e1",
            "Fix auth bug",
            "Fixed JWT validation",
            "Full content here",
            MemoryCategory.BUGFIX,
            List.of(new MemoryTag("priority", "high")),
            Instant.now(),
            List.of("src/Auth.java"));

    store
        .store(entry)
        .compose(v -> store.recall("e1"))
        .onComplete(
            ctx.succeeding(
                recalled -> {
                  assertEquals("e1", recalled.id());
                  assertEquals("Fix auth bug", recalled.title());
                  assertEquals("Fixed JWT validation", recalled.summary());
                  assertEquals(MemoryCategory.BUGFIX, recalled.category());
                  assertEquals(1, recalled.tags().size());
                  assertEquals("priority", recalled.tags().get(0).name());
                  assertEquals(1, recalled.relatedFiles().size());
                  ctx.completeNow();
                }));
  }

  @Test
  void recallNotFound(VertxTestContext ctx) {
    store
        .recall("nonexistent")
        .onComplete(
            ar -> {
              assertTrue(ar.failed());
              assertTrue(ar.cause().getMessage().contains("not found"));
              ctx.completeNow();
            });
  }

  @Test
  void searchByKeyword(VertxTestContext ctx) {
    MemoryEntry e1 =
        new MemoryEntry(
            "e1",
            "Fix auth bug",
            "JWT validation issue",
            "Full",
            MemoryCategory.BUGFIX,
            List.of(),
            Instant.now(),
            List.of());
    MemoryEntry e2 =
        new MemoryEntry(
            "e2",
            "Add caching",
            "Redis integration",
            "Full",
            MemoryCategory.FEATURE,
            List.of(),
            Instant.now(),
            List.of());

    store
        .store(e1)
        .compose(v -> store.store(e2))
        .compose(v -> store.search(new MemoryQuery("auth", null, null, 10)))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  assertEquals("e1", results.get(0).id());
                  ctx.completeNow();
                }));
  }

  @Test
  void searchByCategory(VertxTestContext ctx) {
    MemoryEntry e1 =
        new MemoryEntry(
            "e1", "Bug one", "s", "f", MemoryCategory.BUGFIX, List.of(), Instant.now(), List.of());
    MemoryEntry e2 =
        new MemoryEntry(
            "e2",
            "Feature one",
            "s",
            "f",
            MemoryCategory.FEATURE,
            List.of(),
            Instant.now(),
            List.of());

    store
        .store(e1)
        .compose(v -> store.store(e2))
        .compose(
            v -> store.search(new MemoryQuery(null, List.of(MemoryCategory.FEATURE), null, 10)))
        .onComplete(
            ctx.succeeding(
                results -> {
                  assertEquals(1, results.size());
                  assertEquals("e2", results.get(0).id());
                  ctx.completeNow();
                }));
  }

  @Test
  void getRecentIndex(VertxTestContext ctx) {
    Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
    Instant t2 = Instant.parse("2026-01-02T00:00:00Z");
    MemoryEntry e1 =
        new MemoryEntry(
            "e1", "Older", "s", "f", MemoryCategory.OBSERVATION, List.of(), t1, List.of());
    MemoryEntry e2 =
        new MemoryEntry(
            "e2", "Newer", "s", "f", MemoryCategory.OBSERVATION, List.of(), t2, List.of());

    store
        .store(e1)
        .compose(v -> store.store(e2))
        .compose(v -> store.getRecentIndex(5))
        .onComplete(
            ctx.succeeding(
                items -> {
                  assertEquals(2, items.size());
                  // Newest first
                  assertEquals("e2", items.get(0).id());
                  assertEquals("e1", items.get(1).id());
                  ctx.completeNow();
                }));
  }

  @Test
  void storeOverwritesExisting(VertxTestContext ctx) {
    MemoryEntry v1 =
        new MemoryEntry(
            "e1", "V1", "s1", "f1", MemoryCategory.BUGFIX, List.of(), Instant.now(), List.of());
    MemoryEntry v2 =
        new MemoryEntry(
            "e1", "V2", "s2", "f2", MemoryCategory.FEATURE, List.of(), Instant.now(), List.of());

    store
        .store(v1)
        .compose(v -> store.store(v2))
        .compose(v -> store.recall("e1"))
        .onComplete(
            ctx.succeeding(
                recalled -> {
                  assertEquals("V2", recalled.title());
                  assertEquals(MemoryCategory.FEATURE, recalled.category());
                  ctx.completeNow();
                }));
  }

  @Test
  void serializeDeserializeTags() {
    List<MemoryTag> tags = List.of(new MemoryTag("k1", "v1"), new MemoryTag("k2", "v2"));
    String json = SqliteMemoryStore.serializeTags(tags);
    List<MemoryTag> restored = SqliteMemoryStore.deserializeTags(json);
    assertEquals(2, restored.size());
    assertEquals("k1", restored.get(0).name());
    assertEquals("v2", restored.get(1).value());
  }

  @Test
  void serializeDeserializeEmptyTags() {
    assertEquals("[]", SqliteMemoryStore.serializeTags(List.of()));
    assertEquals(List.of(), SqliteMemoryStore.deserializeTags("[]"));
    assertEquals(List.of(), SqliteMemoryStore.deserializeTags(null));
  }
}
