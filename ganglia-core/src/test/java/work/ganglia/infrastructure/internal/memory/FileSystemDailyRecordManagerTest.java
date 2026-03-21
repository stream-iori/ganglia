package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@ExtendWith(VertxExtension.class)
public class FileSystemDailyRecordManagerTest {

  @Test
  public void testRecordCreatesDirectoryAndFile(
      Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
    String basePath = tempDir.resolve("memory").toString();
    FileSystemDailyRecordManager manager = new FileSystemDailyRecordManager(vertx, basePath);

    String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    String expectedFile = basePath + "/daily-" + dateStr + ".md";

    manager
        .record("session-1", "My Goal", "My Accomplishments")
        .compose(
            v -> {
              assertTrue(new File(basePath).exists());
              assertTrue(new File(basePath).isDirectory());
              assertTrue(new File(expectedFile).exists());
              return vertx.fileSystem().readFile(expectedFile);
            })
        .onComplete(
            testContext.succeeding(
                buffer -> {
                  String content = buffer.toString();
                  assertTrue(content.contains("session-1"));
                  assertTrue(content.contains("My Goal"));
                  assertTrue(content.contains("My Accomplishments"));
                  testContext.completeNow();
                }));
  }
}
