package work.ganglia.infrastructure.internal.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.port.internal.memory.DailyRecordManager;

@ExtendWith(VertxExtension.class)
public class DailyRecordManagerTest {

  private DailyRecordManager manager;
  private String testPath;

  @BeforeEach
  void setUp(Vertx vertx, @TempDir Path tempDir) {
    testPath = tempDir.toString();
    manager = new FileSystemDailyRecordManager(vertx, testPath);
  }

  @Test
  void testRecordAndAppend(Vertx vertx, VertxTestContext testContext) {
    String sessionId = "test-session";
    String entry = "- Accomplished task A\n- Learned fact B";
    String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    String expectedFile = testPath + "/daily-" + dateStr + ".md";

    manager
        .record(sessionId, "Test Goal", entry)
        .compose(v -> vertx.fileSystem().readFile(expectedFile))
        .onComplete(
            testContext.succeeding(
                buffer -> {
                  testContext.verify(
                      () -> {
                        String content = buffer.toString();
                        assertTrue(content.contains("# Daily Record: " + dateStr));
                        assertTrue(content.contains("## [Session: " + sessionId + "]"));
                        assertTrue(content.contains("Test Goal"));
                        assertTrue(content.contains("- Accomplished task A"));
                        testContext.completeNow();
                      });
                }));
  }
}
