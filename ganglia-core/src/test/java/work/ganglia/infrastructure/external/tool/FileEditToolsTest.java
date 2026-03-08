package work.ganglia.infrastructure.external.tool;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.util.PathSanitizer;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.infrastructure.external.tool.model.ToolInvokeResult;
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
    private String testFileName = "test.txt";

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir) {
        tools = new FileEditTools(vertx, new PathSanitizer(tempDir.toString()));
        testFilePath = tempDir.resolve(testFileName).toString();

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
        args.put("file_path", testFileName);
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

                    // Verify diff
                    String diff = result.diff();
                    assertTrue(diff != null && !diff.isEmpty(), "Diff should not be empty");
                    assertTrue(diff.contains("-target block"), "Diff should contain removal");
                    assertTrue(diff.contains("+updated block"), "Diff should contain addition");
                    assertTrue(diff.contains(testFileName), "Diff should contain filename");

                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testWriteFileNew(Vertx vertx, VertxTestContext testContext) {
        String newFileName = "new_file.txt";
        String newContent = "Brand new content\nWith multiple lines\n";
        
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", newFileName);
        args.put("content", newContent);

        tools.execute(new ToolCall("id", "write_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    String written = vertx.fileSystem().readFileBlocking(testFilePath.replace(testFileName, newFileName)).toString();
                    assertEquals(newContent, written);
                    
                    // Diff for new file should show everything added from /dev/null or empty
                    assertTrue(result.diff().contains("+Brand new content"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testWriteFileOverwrite(Vertx vertx, VertxTestContext testContext) {
        String newContent = "Overwritten content\n";
        
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFileName);
        args.put("content", newContent);

        tools.execute(new ToolCall("id", "write_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    String written = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertEquals(newContent, written);
                    
                    // Verify diff contains deletions and additions
                    assertTrue(result.diff().contains("-line 1"));
                    assertTrue(result.diff().contains("+Overwritten content"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testApplyPatch(Vertx vertx, VertxTestContext testContext) {
        // Unified diff format
        String patch = """
            --- test.txt
            +++ test.txt
            @@ -1,5 +1,5 @@
             line 1
            -line 2
            +line 2 patched
             target block
             line 4
             line 5
            """;
        
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFileName);
        args.put("patch", patch);

        tools.execute(new ToolCall("id", "apply_patch", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    String updated = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertTrue(updated.contains("line 2 patched"));
                    assertTrue(!updated.contains("line 2\n"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testMatchFailure(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", testFileName);
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
        args.put("file_path", testFileName);
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
        args.put("file_path", testFileName);
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

    @Test
    void testPathTraversalProtection(Vertx vertx, VertxTestContext testContext) {
        Map<String, Object> args = new HashMap<>();
        args.put("file_path", "../escaped.txt");
        args.put("content", "should fail");

        tools.execute(new ToolCall("id", "write_file", args), null)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
                    assertTrue(result.output().contains("Security/Validation Error"));
                    testContext.completeNow();
                });
            }));
    }
}
