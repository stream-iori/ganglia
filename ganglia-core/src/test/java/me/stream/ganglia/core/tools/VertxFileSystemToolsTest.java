package me.stream.ganglia.core.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.tools.VertxFileSystemTools;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class VertxFileSystemToolsTest {

    private VertxFileSystemTools tools;

    @BeforeEach
    void setUp(Vertx vertx) {
        tools = new VertxFileSystemTools(vertx);
    }

    @Test
    void testWriteAndRead(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String filePath = tempDir.resolve("test.txt").toString();
        String content = "Hello, Ganglia!";

        tools.execute(new ToolCall("id", "write_file", Map.of("path", filePath, "content", content)), null)
            .compose(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("Successfully written"));
                });
                return tools.execute(new ToolCall("id", "vertx_read", Map.of("path", filePath)), null);
            })
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertEquals(content, res.output());
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testLs(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String filePath = tempDir.resolve("test_ls.txt").toString();
        
        vertx.fileSystem().writeFile(filePath, io.vertx.core.buffer.Buffer.buffer("test"))
            .compose(v -> tools.execute(new ToolCall("id", "vertx_ls", Map.of("path", tempDir.toString())), null))
            .onComplete(testContext.succeeding(res -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, res.status());
                    assertTrue(res.output().contains("test_ls.txt"));
                    testContext.completeNow();
                });
            }));
    }
}
