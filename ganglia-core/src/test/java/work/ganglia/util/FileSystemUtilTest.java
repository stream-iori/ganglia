package work.ganglia.util;

import static org.junit.jupiter.api.Assertions.*;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(VertxExtension.class)
public class FileSystemUtilTest {

  @Test
  public void testEnsureDirectoryExists(
      Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
    String testDir = tempDir.resolve("new-dir").toString();

    FileSystemUtil.ensureDirectoryExists(vertx, testDir)
        .compose(
            v -> {
              assertTrue(new File(testDir).exists());
              assertTrue(new File(testDir).isDirectory());
              // Test second call (already exists) - should be idempotent
              return FileSystemUtil.ensureDirectoryExists(vertx, testDir);
            })
        .onComplete(testContext.succeeding(v -> testContext.completeNow()));
  }

  @Test
  public void testEnsureNestedDirectoryExists(
      Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
    String nestedDir = tempDir.resolve("a/b/c").toString();

    FileSystemUtil.ensureDirectoryExists(vertx, nestedDir)
        .compose(
            v -> {
              assertTrue(new File(nestedDir).exists());
              assertTrue(new File(nestedDir).isDirectory());
              return vertx.fileSystem().exists(nestedDir);
            })
        .onComplete(
            testContext.succeeding(
                exists -> {
                  assertTrue(exists);
                  testContext.completeNow();
                }));
  }

  @Test
  public void testEnsureFileWithDefault(
      Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
    String testFile = tempDir.resolve("subdir/test-file.txt").toString();
    String content = "Hello World";

    FileSystemUtil.ensureFileWithDefault(vertx, testFile, content)
        .compose(
            v -> {
              assertTrue(new File(testFile).exists());
              return vertx.fileSystem().readFile(testFile);
            })
        .compose(
            buffer -> {
              assertEquals(content, buffer.toString());
              // Test second call (should not overwrite existing file)
              return FileSystemUtil.ensureFileWithDefault(vertx, testFile, "Should not be written");
            })
        .compose(v -> vertx.fileSystem().readFile(testFile))
        .onComplete(
            testContext.succeeding(
                buffer -> {
                  assertEquals(content, buffer.toString());
                  testContext.completeNow();
                }));
  }

  @Test
  public void testEnsureFileWithBufferDefault(
      Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
    String testFile = tempDir.resolve("binary.bin").toString();
    Buffer content = Buffer.buffer(new byte[] {1, 2, 3});

    FileSystemUtil.ensureFileWithDefault(vertx, testFile, content)
        .compose(v -> vertx.fileSystem().readFile(testFile))
        .onComplete(
            testContext.succeeding(
                buffer -> {
                  assertArrayEquals(content.getBytes(), buffer.getBytes());
                  testContext.completeNow();
                }));
  }
}
