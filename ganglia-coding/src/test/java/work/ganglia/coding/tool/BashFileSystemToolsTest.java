package work.ganglia.coding.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @Test
  void testReadFilesBatch(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path f1 = tempDir.resolve("a.txt");
    Files.writeString(f1, "Content A");
    Path f2 = tempDir.resolve("b.txt");
    Files.writeString(f2, "Content B\nLine 2");

    tools
        .execute("read_files", Map.of("paths", List.of(f1.toString(), f2.toString())), null, null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        assertTrue(res.output().contains("Content A"));
                        assertTrue(res.output().contains("Content B"));
                        assertTrue(res.output().contains("Line 2"));
                        testContext.completeNow();
                      });
                }));
  }

  static Stream<Arguments> paginationProvider() {
    return Stream.of(
        arguments(0, 3, List.of("Line 0", "Line 1", "Line 2"), List.of("Line 3"), "First page"),
        arguments(3, 4, List.of("Line 3", "Line 6"), List.of("Line 2", "Line 7"), "Middle page"),
        arguments(
            8, 5, List.of("Line 8", "Line 9"), List.of("Hint: More lines available"), "Last page"));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("paginationProvider")
  @DisplayName("ReadFile Pagination Parameterized Test")
  void testReadFilePagination(
      int offset,
      int limit,
      List<String> contains,
      List<String> notContains,
      String description,
      Vertx vertx,
      VertxTestContext testContext)
      throws Exception {
    Path file = tempDir.resolve("large.txt");
    if (!Files.exists(file)) {
      StringBuilder content = new StringBuilder();
      for (int i = 0; i < 10; i++) {
        content.append("Line ").append(i).append("\n");
      }
      Files.writeString(file, content.toString());
    }

    tools
        .execute(
            "read_file",
            Map.of("path", file.toString(), "offset", offset, "limit", limit),
            null,
            null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        for (String s : contains) {
                          assertTrue(res.output().contains(s), "Output should contain: " + s);
                        }
                        for (String s : notContains) {
                          assertFalse(res.output().contains(s), "Output should NOT contain: " + s);
                        }
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testReadFilesWithErrors(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path f1 = tempDir.resolve("valid.txt");
    Files.writeString(f1, "Valid Content");
    String invalidPath = tempDir.resolve("missing.txt").toString();

    tools
        .execute("read_files", Map.of("paths", List.of(f1.toString(), invalidPath)), null, null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        assertTrue(res.output().contains("Valid Content"));
                        assertTrue(res.output().contains("[ERROR: File not found"));
                        testContext.completeNow();
                      });
                }));
  }
}
