package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.tools.BashFileSystemTools;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class BashFileSystemToolsTest {

    private BashFileSystemTools tools;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new BashFileSystemTools(vertx);
    }

    @Test
    void testGrepSearch(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) throws Exception {
        Path file1 = tempDir.resolve("file1.txt");
        Files.writeString(file1, "This is a test file with some pattern.");
        Path file2 = tempDir.resolve("file2.txt");
        Files.writeString(file2, "Another file without that.");

        tools.execute(new ToolCall("id", "grep_search", Map.of("path", tempDir.toString(), "pattern", "pattern")), null)
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("file1.txt"));
                    assertTrue(res.output().contains("pattern"));
                    assertFalse(res.output().contains("file2.txt"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testGlob(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) throws Exception {
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
}
