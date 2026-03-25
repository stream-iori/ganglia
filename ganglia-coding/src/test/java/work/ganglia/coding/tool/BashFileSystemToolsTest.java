package work.ganglia.coding.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.coding.tool.util.LocalCommandExecutor;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.util.PathSanitizer;

@ExtendWith(VertxExtension.class)
public class BashFileSystemToolsTest {

  @TempDir Path tempDir;

  private BashFileSystemTools tools;

  @BeforeEach
  void setUp(Vertx vertx) throws Exception {
    // Resolve real path to handle macOS symlinks
    Path realTemp = tempDir.toRealPath();
    PathSanitizer sanitizer = new PathSanitizer(realTemp.toString());
    tools = new BashFileSystemTools(new LocalCommandExecutor(vertx), sanitizer);
  }

  @Test
  void testGrepSearch(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path file = tempDir.resolve("test.txt");
    Files.writeString(file, "Hello World\nJava is fun");

    tools
        .execute("grep_search", Map.of("path", tempDir.toString(), "pattern", "Java"), null, null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        assertTrue(res.output().contains("test.txt"));
                        assertTrue(res.output().contains("Java is fun"));
                        testContext.completeNow();
                      });
                }));
  }
}
