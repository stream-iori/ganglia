package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

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
class UserProfileModuleTest {

  private UserProfileModule module;
  private FileSystemLongTermMemory longTermMemory;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.longTermMemory = new FileSystemLongTermMemory(vertx, tempDir.toString(), "MEMORY.md");
    this.module = new UserProfileModule(longTermMemory);
  }

  @Test
  void id_returnsUserProfile() {
    assertEquals("user-profile", module.id());
  }

  @Test
  void provideContext_withoutProfile_returnsGuidanceOnly(Vertx vertx, VertxTestContext ctx) {
    // User profile not initialized — no USER.md file exists
    module
        .provideContext(null)
        .onComplete(
            ctx.succeeding(
                fragments ->
                    ctx.verify(
                        () -> {
                          assertEquals(1, fragments.size());
                          ContextFragment guidance = fragments.get(0);
                          assertEquals("Memory Guidance", guidance.name());
                          assertTrue(
                              guidance.content().contains("remember(fact, target=\"user\")"));
                          assertTrue(guidance.content().contains("project"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void provideContext_withProfile_returnsBothFragments(Vertx vertx, VertxTestContext ctx) {
    longTermMemory
        .ensureInitialized(LongTermMemory.USER_PROFILE_TOPIC)
        .compose(v -> longTermMemory.appendUserProfile("- Prefers concise answers"))
        .compose(v -> module.provideContext(null))
        .onComplete(
            ctx.succeeding(
                fragments ->
                    ctx.verify(
                        () -> {
                          assertEquals(2, fragments.size());

                          ContextFragment profile = fragments.get(0);
                          assertEquals("User Profile", profile.name());
                          assertTrue(profile.content().contains("Prefers concise answers"));

                          ContextFragment guidance = fragments.get(1);
                          assertEquals("Memory Guidance", guidance.name());

                          // Profile has higher priority (lower number = higher)
                          assertTrue(
                              profile.priority() < guidance.priority(),
                              "Profile fragment should have higher priority than guidance");
                          ctx.completeNow();
                        })));
  }

  @Test
  void onEvent_sessionClosed_succeeds(Vertx vertx, VertxTestContext ctx) {
    MemoryEvent event =
        new MemoryEvent(MemoryEvent.EventType.SESSION_CLOSED, "session-1", null, null);

    module
        .onEvent(event)
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          // After SESSION_CLOSED, ensureUserProfileInitialized is called.
                          // Verify no error thrown.
                          ctx.completeNow();
                        })));
  }

  @Test
  void onEvent_turnCompleted_succeeds(Vertx vertx, VertxTestContext ctx) {
    MemoryEvent event =
        new MemoryEvent(MemoryEvent.EventType.TURN_COMPLETED, "session-1", null, null);

    module
        .onEvent(event)
        .onComplete(
            ctx.succeeding(
                v ->
                    ctx.verify(
                        () -> {
                          // Should not throw
                          ctx.completeNow();
                        })));
  }
}
