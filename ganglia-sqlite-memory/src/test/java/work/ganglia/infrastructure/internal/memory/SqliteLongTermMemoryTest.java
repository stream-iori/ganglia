package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.LongTermMemory;

@ExtendWith(VertxExtension.class)
class SqliteLongTermMemoryTest {

  private SqliteConnectionManager cm;
  private SqliteLongTermMemory memory;

  @BeforeEach
  void setUp(Vertx vertx, VertxTestContext ctx) {
    cm = new SqliteConnectionManager(vertx);
    cm.initSchema()
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                memory = new SqliteLongTermMemory(cm);
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
  void ensureInitializedCreatesDefaultTemplate(VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertTrue(content.contains("# Project Memory"));
                  assertTrue(content.contains("## Architecture Decisions"));
                  ctx.completeNow();
                }));
  }

  @Test
  void ensureInitializedCreatesUserProfileTemplate(VertxTestContext ctx) {
    memory
        .ensureUserProfileInitialized()
        .compose(v -> memory.readUserProfile())
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertTrue(content.contains("# User Profile"));
                  assertTrue(content.contains("## Technical Background"));
                  ctx.completeNow();
                }));
  }

  @Test
  void appendAndRead(VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.append("New fact"))
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertTrue(content.contains("New fact"));
                  ctx.completeNow();
                }));
  }

  @Test
  void replaceContent(VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.replace(LongTermMemory.DEFAULT_TOPIC, "Replaced content"))
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertEquals("Replaced content", content);
                  ctx.completeNow();
                }));
  }

  @Test
  void getSize(VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.replace(LongTermMemory.DEFAULT_TOPIC, "12345"))
        .compose(v -> memory.getSize(LongTermMemory.DEFAULT_TOPIC))
        .onComplete(
            ctx.succeeding(
                size -> {
                  assertEquals(5, size);
                  ctx.completeNow();
                }));
  }

  @Test
  void readUninitializedTopicReturnsEmpty(VertxTestContext ctx) {
    memory
        .read("nonexistent")
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertEquals("", content);
                  ctx.completeNow();
                }));
  }

  @Test
  void ensureInitializedIsIdempotent(VertxTestContext ctx) {
    memory
        .ensureInitialized()
        .compose(v -> memory.append("Added"))
        .compose(v -> memory.ensureInitialized()) // Should NOT overwrite
        .compose(v -> memory.read())
        .onComplete(
            ctx.succeeding(
                content -> {
                  assertTrue(content.contains("Added"));
                  ctx.completeNow();
                }));
  }
}
