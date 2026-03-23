package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.port.internal.memory.model.*;

@ExtendWith(VertxExtension.class)
class FileSystemMemoryStoreTest {

  private FileSystemMemoryStore store;
  private Path tempPath;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.tempPath = tempDir;
    this.store = new FileSystemMemoryStore(vertx, tempDir.toString());
  }

  @Test
  void testStoreAndRecall(Vertx vertx, VertxTestContext testContext) {
    String id = UUID.randomUUID().toString();
    MemoryEntry entry =
        new MemoryEntry(
            id,
            "Test Title",
            "Summary content",
            "Full content of the memory",
            MemoryCategory.DECISION,
            List.of(new MemoryTag("project", "ganglia")),
            Instant.now(),
            List.of("file1.txt"));

    store
        .store(entry)
        .compose(v -> store.recall(id))
        .onComplete(
            testContext.succeeding(
                recalled -> {
                  testContext.verify(
                      () -> {
                        assertEquals(entry.id(), recalled.id());
                        assertEquals(entry.title(), recalled.title());
                        assertEquals(entry.fullContent(), recalled.fullContent());
                        assertEquals(MemoryCategory.DECISION, recalled.category());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGetRecentIndex(Vertx vertx, VertxTestContext testContext) {
    Instant now = Instant.now();
    MemoryEntry e1 = createEntry("1", "Title 1", now.minusSeconds(10));
    MemoryEntry e2 = createEntry("2", "Title 2", now);

    store
        .store(e1)
        .compose(v -> store.store(e2))
        .compose(v -> store.getRecentIndex(10))
        .onComplete(
            testContext.succeeding(
                index -> {
                  testContext.verify(
                      () -> {
                        assertEquals(2, index.size());
                        assertEquals("2", index.get(0).id()); // Sorted by timestamp desc
                        assertEquals("1", index.get(1).id());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSearchByKeyword(Vertx vertx, VertxTestContext testContext) {
    MemoryEntry e1 = createEntry("1", "Important Bugfix", Instant.now());
    MemoryEntry e2 = createEntry("2", "Feature Implementation", Instant.now());

    store
        .store(e1)
        .compose(v -> store.store(e2))
        .compose(v -> store.search(new MemoryQuery("bugfix", null, null, 10)))
        .onComplete(
            testContext.succeeding(
                results -> {
                  testContext.verify(
                      () -> {
                        assertEquals(1, results.size());
                        assertEquals("1", results.get(0).id());
                        testContext.completeNow();
                      });
                }));
  }

  private MemoryEntry createEntry(String id, String title, Instant timestamp) {
    return new MemoryEntry(
        id,
        title,
        "summary",
        "content",
        MemoryCategory.UNKNOWN,
        Collections.emptyList(),
        timestamp,
        Collections.emptyList());
  }
}
