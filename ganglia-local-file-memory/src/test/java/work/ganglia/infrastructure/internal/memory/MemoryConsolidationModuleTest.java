package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.LongTermMemory;
import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.prompt.ContextFragment;

@ExtendWith(VertxExtension.class)
class MemoryConsolidationModuleTest {

  private MemoryConsolidationModule module;
  private FileSystemLongTermMemory longTermMemory;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.longTermMemory = new FileSystemLongTermMemory(vertx, tempDir.toString(), "MEMORY.md");
    // Low threshold for testing (100 chars)
    this.module = new MemoryConsolidationModule(longTermMemory, 100);
  }

  @Test
  void id_returnsMemoryConsolidation() {
    assertEquals("memory-consolidation", module.id());
  }

  @Test
  void provideContext_noNudge_returnsEmpty() {
    List<ContextFragment> fragments = module.provideContext(null).result();
    assertTrue(fragments.isEmpty());
  }

  @Test
  void sessionClosed_smallMemory_noNudge(Vertx vertx, VertxTestContext ctx) {
    longTermMemory
        .ensureInitialized(LongTermMemory.DEFAULT_TOPIC)
        .compose(v -> longTermMemory.ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC))
        .compose(
            v -> {
              MemoryEvent close =
                  new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s1", null, null, 3, 5);
              return module.onEvent(close);
            })
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          List<ContextFragment> fragments = module.provideContext(null).result();
                          assertTrue(fragments.isEmpty(), "Small memory should not trigger nudge");
                          ctx.completeNow();
                        })));
  }

  @Test
  void sessionClosed_largeProjectMemory_triggersNudge(Vertx vertx, VertxTestContext ctx) {
    // Fill project memory beyond threshold
    String largeContent = "x".repeat(150);

    longTermMemory
        .ensureInitialized(LongTermMemory.DEFAULT_TOPIC)
        .compose(v -> longTermMemory.ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC))
        .compose(v -> longTermMemory.append(LongTermMemory.DEFAULT_TOPIC, largeContent))
        .compose(
            v -> {
              MemoryEvent close =
                  new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s1", null, null, 3, 5);
              return module.onEvent(close);
            })
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          List<ContextFragment> fragments = module.provideContext(null).result();
                          assertTrue(
                              fragments.stream()
                                  .anyMatch(f -> f.name().equals("Memory Consolidation Needed")),
                              "Large project memory should trigger consolidation nudge");
                          ctx.completeNow();
                        })));
  }

  @Test
  void sessionClosed_largeUserProfile_triggersNudge(Vertx vertx, VertxTestContext ctx) {
    String largeContent = "y".repeat(150);

    longTermMemory
        .ensureInitialized(LongTermMemory.DEFAULT_TOPIC)
        .compose(v -> longTermMemory.ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC))
        .compose(v -> longTermMemory.append(LongTermMemory.USER_PROFILE_TOPIC, largeContent))
        .compose(
            v -> {
              MemoryEvent close =
                  new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s1", null, null, 3, 5);
              return module.onEvent(close);
            })
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          List<ContextFragment> fragments = module.provideContext(null).result();
                          assertTrue(
                              fragments.stream()
                                  .anyMatch(
                                      f -> f.name().equals("User Profile Consolidation Needed")),
                              "Large user profile should trigger consolidation nudge");
                          ctx.completeNow();
                        })));
  }

  @Test
  void nudge_consumedOnRead(Vertx vertx, VertxTestContext ctx) {
    String largeContent = "z".repeat(150);

    longTermMemory
        .ensureInitialized(LongTermMemory.DEFAULT_TOPIC)
        .compose(v -> longTermMemory.ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC))
        .compose(v -> longTermMemory.append(LongTermMemory.DEFAULT_TOPIC, largeContent))
        .compose(
            v ->
                module.onEvent(
                    new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s1", null, null, 3, 5)))
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          // First read consumes
                          List<ContextFragment> first = module.provideContext(null).result();
                          assertFalse(first.isEmpty());

                          // Second read is empty
                          List<ContextFragment> second = module.provideContext(null).result();
                          assertTrue(second.isEmpty(), "Nudge should be consumed after first read");
                          ctx.completeNow();
                        })));
  }

  @Test
  void turnCompleted_doesNotTriggerCheck(Vertx vertx, VertxTestContext ctx) {
    String largeContent = "a".repeat(150);

    longTermMemory
        .ensureInitialized(LongTermMemory.DEFAULT_TOPIC)
        .compose(v -> longTermMemory.ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC))
        .compose(v -> longTermMemory.append(LongTermMemory.DEFAULT_TOPIC, largeContent))
        .compose(
            v ->
                module.onEvent(
                    new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "s1", null, null, 1, 0)))
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          List<ContextFragment> fragments = module.provideContext(null).result();
                          assertTrue(
                              fragments.isEmpty(),
                              "TURN_COMPLETED should not trigger consolidation check");
                          ctx.completeNow();
                        })));
  }
}
