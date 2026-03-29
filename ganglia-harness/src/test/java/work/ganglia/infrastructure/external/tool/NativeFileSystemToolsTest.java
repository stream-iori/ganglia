package work.ganglia.infrastructure.external.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
import work.ganglia.util.PathSanitizer;

@ExtendWith(VertxExtension.class)
public class NativeFileSystemToolsTest {

  @TempDir Path tempDir;

  private NativeFileSystemTools tools;

  @BeforeEach
  void setUp(Vertx vertx) throws Exception {
    Path realTemp = tempDir.toRealPath();
    PathSanitizer sanitizer = new PathSanitizer(realTemp.toString());
    tools = new NativeFileSystemTools(vertx, sanitizer);
  }

  @Test
  void testListDirectory(Vertx vertx, VertxTestContext testContext) throws Exception {
    Files.createFile(tempDir.resolve("test.txt"));
    Files.createDirectory(tempDir.resolve("subdir"));

    tools
        .execute("list_directory", Map.of("path", tempDir.toString()), null, null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        assertTrue(res.output().contains("subdir/"));
                        assertTrue(res.output().contains("test.txt"));
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
        // start_line, end_line, contains, not_contains, desc
        arguments(1, 4, List.of("Line 1", "Line 2", "Line 4"), List.of("Line 5"), "First page"),
        arguments(4, 7, List.of("Line 4", "Line 7"), List.of("Line 3", "Line 8"), "Middle page"),
        arguments(
            9,
            15,
            List.of("Line 9", "Line 10"),
            List.of("Hint: More lines available"),
            "Last page"));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("paginationProvider")
  @DisplayName("ReadFile Pagination Parameterized Test (1-based)")
  void testReadFilePagination(
      int startLine,
      int endLine,
      List<String> contains,
      List<String> notContains,
      String description,
      Vertx vertx,
      VertxTestContext testContext)
      throws Exception {
    Path file = tempDir.resolve("large.txt");
    if (!Files.exists(file)) {
      StringBuilder content = new StringBuilder();
      for (int i = 1; i <= 10; i++) {
        content.append("Line ").append(i).append("\n");
      }
      Files.writeString(file, content.toString());
    }

    tools
        .execute(
            "read_file",
            Map.of("path", file.toString(), "start_line", startLine, "end_line", endLine),
            null,
            null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        for (String s : contains) {
                          assertTrue(
                              res.output().contains(s),
                              "Output should contain: " + s + " but got:\n" + res.output());
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
                        assertTrue(
                            res.output().contains("[ERROR: File not found")
                                || res.output().contains("java.nio.file.NoSuchFileException"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testLongLineTruncation(Vertx vertx, VertxTestContext testContext) throws Exception {
    Path file = tempDir.resolve("long.txt");
    String longLine = "A".repeat(3000);
    Files.writeString(file, longLine + "\nShort line");

    tools
        .execute(
            "read_file",
            Map.of("path", file.toString(), "start_line", 1, "end_line", 2),
            null,
            null)
        .onComplete(
            testContext.succeeding(
                res -> {
                  testContext.verify(
                      () -> {
                        assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                        assertTrue(res.output().contains("[...Line truncated...]"));
                        assertTrue(res.output().contains("Short line"));
                        testContext.completeNow();
                      });
                }));
  }
}
