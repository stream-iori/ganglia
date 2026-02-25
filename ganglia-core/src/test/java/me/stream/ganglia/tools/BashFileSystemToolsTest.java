package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.util.PathSanitizer;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class BashFileSystemToolsTest {

    @TempDir
    Path tempDir;

    private BashFileSystemTools tools;

    @BeforeEach
    void setUp(Vertx vertx) throws Exception {
        // Resolve real path to handle macOS symlinks
        Path realTemp = tempDir.toRealPath();
        PathSanitizer sanitizer = new PathSanitizer(realTemp.toString());
        tools = new BashFileSystemTools(vertx, sanitizer);
    }

    @Test
    void testGrepSearch(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World\nJava is fun");

        tools.execute("grep_search", Map.of("path", tempDir.toString(), "pattern", "Java"), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("test.txt"));
                    assertTrue(res.output().contains("Java is fun"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testGlob(Vertx vertx, VertxTestContext testContext) throws Exception {
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("a.java"), "java");
        Files.writeString(tempDir.resolve("subdir/b.java"), "java");
        Files.writeString(tempDir.resolve("c.txt"), "text");

        tools.execute(new ToolCall("id", "glob", Map.of("path", tempDir.toString(), "pattern", "**/*.java")), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("a.java"));
                    assertTrue(res.output().contains("subdir/b.java"));
                    assertFalse(res.output().contains("c.txt"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testReadFilePagination(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path file = tempDir.resolve("large.txt");
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            content.append("Line ").append(i).append("\n");
        }
        Files.writeString(file, content.toString());

        // Test first page
        tools.execute("read_file", Map.of("path", file.toString(), "offset", 0, "limit", 3), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("Line 0"));
                    assertTrue(res.output().contains("Line 1"));
                    assertTrue(res.output().contains("Line 2"));
                    assertFalse(res.output().contains("Line 3"));
                    assertTrue(res.output().contains("--- [Lines 0 to 3 of 10] ---"));
                    assertTrue(res.output().contains("Hint: More lines available"));
                });

                // Test middle page
                tools.execute("read_file", Map.of("path", file.toString(), "offset", 3, "limit", 4), null)
                    .onComplete(testContext.succeeding(res2 -> {
                        testContext.verify(() -> {
                            assertEquals(ToolInvokeResult.Status.SUCCESS, res2.status());
                            assertTrue(res2.output().contains("Line 3"));
                            assertTrue(res2.output().contains("Line 6"));
                            assertFalse(res2.output().contains("Line 2"));
                            assertFalse(res2.output().contains("Line 7"));
                            assertTrue(res2.output().contains("--- [Lines 3 to 7 of 10] ---"));
                        });

                        // Test last page
                        tools.execute("read_file", Map.of("path", file.toString(), "offset", 8, "limit", 5), null)
                            .onComplete(testContext.succeeding(res3 -> {
                                testContext.verify(() -> {
                                    assertEquals(ToolInvokeResult.Status.SUCCESS, res3.status());
                                    assertTrue(res3.output().contains("Line 8"));
                                    assertTrue(res3.output().contains("Line 9"));
                                    assertTrue(res3.output().contains("--- [Lines 8 to 10 of 10] ---"));
                                    assertFalse(res3.output().contains("Hint: More lines available"));
                                    testContext.completeNow();
                                });
                            }));
                    }));
            }));
    }

    @Test
    void testReadFilesBatch(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path f1 = tempDir.resolve("a.txt");
        Files.writeString(f1, "Content A");
        Path f2 = tempDir.resolve("b.txt");
        Files.writeString(f2, "Content B\nLine 2");

        tools.execute("read_files", Map.of("paths", List.of(f1.toString(), f2.toString())), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("--- FILE: " + f1.toRealPath().toString() + " ---"));
                    assertTrue(res.output().contains("Content A"));
                    assertTrue(res.output().contains("--- FILE: " + f2.toRealPath().toString() + " ---"));
                    assertTrue(res.output().contains("Content B"));
                    assertTrue(res.output().contains("Line 2"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testReadFilesWithErrors(Vertx vertx, VertxTestContext testContext) throws Exception {
        Path f1 = tempDir.resolve("valid.txt");
        Files.writeString(f1, "Valid Content");
        String invalidPath = tempDir.resolve("missing.txt").toString();

        tools.execute("read_files", Map.of("paths", List.of(f1.toString(), invalidPath)), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("Valid Content"));
                    assertTrue(res.output().contains("[ERROR: File not found"));
                    testContext.completeNow();
                });
            }));
    }
}
