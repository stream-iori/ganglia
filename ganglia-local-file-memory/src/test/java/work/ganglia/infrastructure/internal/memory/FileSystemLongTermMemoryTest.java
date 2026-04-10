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

@ExtendWith(VertxExtension.class)
class FileSystemLongTermMemoryTest {

  private FileSystemLongTermMemory memory;
  private Path tempDir;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    this.tempDir = tempDir;
    this.memory = new FileSystemLongTermMemory(vertx, tempDir.toString(), "MEMORY.md");
  }

  @Test
  void ensureInitialized_createsProjectFile(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> vertx.fileSystem().readFile(tempDir.resolve("MEMORY.md").toString()))
        .onComplete(
            ctx.succeeding(
                buffer ->
                    ctx.verify(
                        () -> {
                          String content = buffer.toString();
                          assertTrue(content.contains("# Project Memory"));
                          assertTrue(content.contains("## Project Conventions"));
                          assertTrue(content.contains("## Architecture Decisions"));
                          assertTrue(content.contains("## Lessons Learned"));
                          // Should NOT contain "User Preferences" anymore (moved to USER.md)
                          assertFalse(content.contains("## User Preferences"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void ensureUserProfileInitialized_createsUserFile(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureUserProfileInitialized()
        .compose(v -> vertx.fileSystem().readFile(tempDir.resolve("USER.md").toString()))
        .onComplete(
            ctx.succeeding(
                buffer ->
                    ctx.verify(
                        () -> {
                          String content = buffer.toString();
                          assertTrue(content.contains("# User Profile"));
                          assertTrue(content.contains("## Communication Style"));
                          assertTrue(content.contains("## Technical Background"));
                          assertTrue(content.contains("## Preferences"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void appendAndRead_projectTopic(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.append("- Java 17 project"))
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content ->
                    ctx.verify(
                        () -> {
                          assertTrue(content.contains("- Java 17 project"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void appendAndRead_userProfileTopic(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureUserProfileInitialized()
        .compose(v -> memory.appendUserProfile("- Prefers concise answers"))
        .compose(v -> memory.readUserProfile())
        .onComplete(
            ctx.succeeding(
                content ->
                    ctx.verify(
                        () -> {
                          assertTrue(content.contains("- Prefers concise answers"));
                          ctx.completeNow();
                        })));
  }

  @Test
  void readUserProfile_returnsEmpty_whenNotInitialized(Vertx vertx, VertxTestContext ctx) {
    memory
        .readUserProfile()
        .onComplete(
            ctx.succeeding(
                content ->
                    ctx.verify(
                        () -> {
                          assertEquals("", content);
                          ctx.completeNow();
                        })));
  }

  @Test
  void projectAndUserProfile_areIndependent(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.ensureUserProfileInitialized())
        .compose(v -> memory.append("- Project fact"))
        .compose(v -> memory.appendUserProfile("- User preference"))
        .compose(
            v -> {
              // Read both and verify independence
              return memory
                  .read()
                  .compose(
                      projectContent ->
                          memory
                              .readUserProfile()
                              .map(
                                  userContent -> {
                                    assertFalse(
                                        projectContent.contains("User preference"),
                                        "Project memory should not contain user content");
                                    assertFalse(
                                        userContent.contains("Project fact"),
                                        "User profile should not contain project content");
                                    return null;
                                  }));
            })
        .onComplete(ctx.succeeding(v -> ctx.completeNow()));
  }

  @Test
  void userProfileTopic_constant_isCorrect() {
    assertEquals("user-profile", LongTermMemory.USER_PROFILE_TOPIC);
  }

  @Test
  void getSize_returnsCharacterCount(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.append("- Test content"))
        .compose(v -> memory.getSize(LongTermMemory.DEFAULT_TOPIC))
        .onComplete(
            ctx.succeeding(
                size ->
                    ctx.verify(
                        () -> {
                          assertTrue(size > 0, "Size should be positive after appending");
                          ctx.completeNow();
                        })));
  }

  @Test
  void replace_overwritesContent(Vertx vertx, VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.append("- Old content"))
        .compose(v -> memory.replace(LongTermMemory.DEFAULT_TOPIC, "# Consolidated\n- New content"))
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content ->
                    ctx.verify(
                        () -> {
                          assertTrue(content.contains("# Consolidated"));
                          assertTrue(content.contains("- New content"));
                          assertFalse(content.contains("- Old content"));
                          ctx.completeNow();
                        })));
  }
}
