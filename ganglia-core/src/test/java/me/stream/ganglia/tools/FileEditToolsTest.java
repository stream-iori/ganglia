package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class FileEditToolsTest {

    private FileEditTools tools;
    private String testFilePath;

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir) {
        tools = new FileEditTools(vertx);
        testFilePath = tempDir.resolve("test.txt").toString();
        
        String content = """
            line 1
            line 2
            target block
            line 4
            line 5
            """;
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(content));
    }

    @Test
    void testSuccessfulReplacement(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFilePath);
        args.put("old_string", "target block");
        args.put("new_string", "updated block");
        args.put("expected_replacements", 1);

        tools.execute(new ToolCall("id", "replace_in_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    String updated = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertTrue(updated.contains("updated block"));
                    assertTrue(!updated.contains("target block"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testMatchFailure(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFilePath);
        args.put("old_string", "non-existent block");
        args.put("new_string", "whatever");

        tools.execute(new ToolCall("id", "replace_in_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                    assertTrue(result.output().contains("MATCH_FAILURE"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testAmbiguityFailure(Vertx vertx, VertxTestContext testContext) {
        // Create file with duplicate content
        String content = "duplicate\nduplicate\n";
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(content));

        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFilePath);
        args.put("old_string", "duplicate");
        args.put("new_string", "single");
        args.put("expected_replacements", 1);

        tools.execute(new ToolCall("id", "replace_in_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                    assertTrue(result.output().contains("AMBIGUITY_FAILURE"));
                    testContext.completeNow();
                });
            }));
    }
    
    @Test
    void testMultipleReplacements(Vertx vertx, VertxTestContext testContext) {
        String content = "item\nitem\n";
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(content));

        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFilePath);
        args.put("old_string", "item");
        args.put("new_string", "replaced");
        args.put("expected_replacements", 2);

        tools.execute(new ToolCall("id", "replace_in_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    String updated = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertEquals("replaced\nreplaced\n", updated);
                    testContext.completeNow();
                });
            }));
    }
}
