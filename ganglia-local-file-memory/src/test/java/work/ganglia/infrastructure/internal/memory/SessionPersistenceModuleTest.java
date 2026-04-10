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

import work.ganglia.port.internal.memory.MemoryEvent;
import work.ganglia.port.internal.prompt.ContextFragment;

@ExtendWith(VertxExtension.class)
class SessionPersistenceModuleTest {

  private SessionPersistenceModule module;
  private FileSystemSessionStore sessionStore;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.sessionStore = new FileSystemSessionStore(vertx, tempDir.toString());
    this.module = new SessionPersistenceModule(sessionStore);
  }

  @Test
  void id_returnsSessionPersistence() {
    assertEquals("session-persistence", module.id());
  }

  @Test
  void provideContext_alwaysEmpty() {
    List<ContextFragment> fragments = module.provideContext(null).result();
    assertTrue(fragments.isEmpty());
  }

  @Test
  void sessionClosed_persistsRecord(Vertx vertx, VertxTestContext ctx) {
    String sessionId = "s1";

    // Simulate turns with goal
    MemoryEvent turn1 =
        new MemoryEvent(
            MemoryEvent.EventType.TURN_COMPLETED, sessionId, "Fix auth bug", null, 1, 3);
    MemoryEvent turn2 =
        new MemoryEvent(
            MemoryEvent.EventType.TURN_COMPLETED, sessionId, "Fix auth bug", null, 2, 7);
    MemoryEvent close =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null, 2, 7);

    module
        .onEvent(turn1)
        .compose(v -> module.onEvent(turn2))
        .compose(v -> module.onEvent(close))
        .compose(v -> sessionStore.getSession(sessionId))
        .onComplete(
            ctx.succeeding(
                record ->
                    ctx.verify(
                        () -> {
                          assertEquals("s1", record.sessionId());
                          assertEquals("Fix auth bug", record.goal());
                          assertEquals(2, record.turnCount());
                          assertEquals(7, record.toolCallCount());
                          assertNotNull(record.startTime());
                          assertNotNull(record.endTime());
                          ctx.completeNow();
                        })));
  }

  @Test
  void sessionClosed_withoutTurns_usesDefaults(Vertx vertx, VertxTestContext ctx) {
    String sessionId = "s2";

    // Directly close without any turns
    MemoryEvent close =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, sessionId, null, null, 0, 0);

    module
        .onEvent(close)
        .compose(v -> sessionStore.getSession(sessionId))
        .onComplete(
            ctx.succeeding(
                record ->
                    ctx.verify(
                        () -> {
                          assertEquals("s2", record.sessionId());
                          assertEquals("(no goal recorded)", record.goal());
                          assertEquals(0, record.turnCount());
                          ctx.completeNow();
                        })));
  }

  @Test
  void separateSessions_independent(Vertx vertx, VertxTestContext ctx) {
    MemoryEvent turn1 =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "s1", "Goal A", null, 1, 2);
    MemoryEvent turn2 =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "s2", "Goal B", null, 1, 5);
    MemoryEvent close1 =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s1", null, null, 1, 2);
    MemoryEvent close2 =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "s2", null, null, 1, 5);

    module
        .onEvent(turn1)
        .compose(v -> module.onEvent(turn2))
        .compose(v -> module.onEvent(close1))
        .compose(v -> module.onEvent(close2))
        .compose(v -> sessionStore.getSession("s1"))
        .compose(
            s1 -> {
              assertEquals("Goal A", s1.goal());
              assertEquals(2, s1.toolCallCount());
              return sessionStore.getSession("s2");
            })
        .onComplete(
            ctx.succeeding(
                s2 ->
                    ctx.verify(
                        () -> {
                          assertEquals("Goal B", s2.goal());
                          assertEquals(5, s2.toolCallCount());
                          ctx.completeNow();
                        })));
  }
}
