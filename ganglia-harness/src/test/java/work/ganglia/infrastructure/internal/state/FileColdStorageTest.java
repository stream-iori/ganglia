package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class FileColdStorageTest {

  @TempDir Path tempDir;

  private FileColdStorage storage;

  @BeforeEach
  void setUp(Vertx vertx) {
    storage = new FileColdStorage(vertx, tempDir.toString());
  }

  @Test
  void write_createsFileWithContent(Vertx vertx, VertxTestContext testContext) {
    storage
        .write("fact-1.json", "{\"detail\": \"some detail\"}")
        .compose(v -> storage.read("fact-1.json"))
        .onComplete(
            testContext.succeeding(
                content ->
                    testContext.verify(
                        () -> {
                          assertEquals("{\"detail\": \"some detail\"}", content);
                          testContext.completeNow();
                        })));
  }

  @Test
  void read_nonExistentRef_returnsNull(Vertx vertx, VertxTestContext testContext) {
    storage
        .read("nonexistent.json")
        .onComplete(
            testContext.succeeding(
                content ->
                    testContext.verify(
                        () -> {
                          assertNull(content);
                          testContext.completeNow();
                        })));
  }

  @Test
  void write_createsParentDirectories(Vertx vertx, VertxTestContext testContext) {
    storage
        .write("nested/dir/fact-2.json", "nested content")
        .compose(v -> storage.read("nested/dir/fact-2.json"))
        .onComplete(
            testContext.succeeding(
                content ->
                    testContext.verify(
                        () -> {
                          assertEquals("nested content", content);
                          testContext.completeNow();
                        })));
  }

  @Test
  void write_overwritesExistingFile(Vertx vertx, VertxTestContext testContext) {
    storage
        .write("fact-3.json", "original")
        .compose(v -> storage.write("fact-3.json", "updated"))
        .compose(v -> storage.read("fact-3.json"))
        .onComplete(
            testContext.succeeding(
                content ->
                    testContext.verify(
                        () -> {
                          assertEquals("updated", content);
                          testContext.completeNow();
                        })));
  }
}
