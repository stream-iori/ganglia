package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.model.MemoryCategory;
import work.ganglia.port.internal.memory.model.TimelineEvent;

@ExtendWith(VertxExtension.class)
class MarkdownTimelineLedgerTest {

  private MarkdownTimelineLedger ledger;
  private Path tempPath;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.tempPath = tempDir;
    this.ledger = new MarkdownTimelineLedger(vertx, tempDir.toString());
  }

  @Test
  void testAppendEvent(Vertx vertx, VertxTestContext testContext) {
    String eventId = UUID.randomUUID().toString();
    TimelineEvent event =
        new TimelineEvent(
            eventId,
            "Refactored memory ports",
            MemoryCategory.REFACTOR,
            Instant.now(),
            List.of("MemoryStore.java", "TimelineLedger.java"));

    ledger
        .appendEvent(event)
        .onComplete(
            testContext.succeeding(
                v -> {
                  testContext.verify(
                      () -> {
                        String content =
                            vertx
                                .fileSystem()
                                .readFileBlocking(
                                    tempPath.resolve(".ganglia/memory/TIMELINE.md").toString())
                                .toString();
                        assertTrue(content.contains("REFACTOR"));
                        assertTrue(content.contains(eventId));
                        assertTrue(content.contains("MemoryStore.java"));
                        testContext.completeNow();
                      });
                }));
  }
}
