package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.tool.CodingToolsFactory;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ToolCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class FileEditIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private Path tempDir;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) throws java.io.IOException {
        this.tempDir = tempDir;
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(Future.failedFuture("Reflection disabled"));

        String projectRoot = tempDir.toRealPath().toString();
        CodingToolsFactory codingToolsFactory = new CodingToolsFactory(vertx, projectRoot);

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)))
            .withExtraToolSets(codingToolsFactory.createToolSets())
            .withExtraContextSources(codingToolsFactory.createContextSources());

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testWriteFileWorkflow(Vertx vertx, VertxTestContext testContext) {
        String testFile = "test.py";
        String content = "def hello():\n    print(\"Hello World\")\n";
        
        ToolCall writeCall = new ToolCall("c1", "write_file", Map.of("file_path", testFile, "content", content));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Writing file.", List.of(writeCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Write a python hello world function to test.py", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    try {
                        Path root = tempDir.toRealPath();
                        assertTrue(java.nio.file.Files.exists(root.resolve(testFile)));
                        assertEquals(content, java.nio.file.Files.readString(root.resolve(testFile)));
                        testContext.completeNow();
                    } catch (java.io.IOException e) {
                        testContext.failNow(e);
                    }
                });
            }));
    }

    @Test
    void testSurgicalEditWorkflow(Vertx vertx, VertxTestContext testContext) {
        String testFile = "main.py";
        String originalContent = "line1\nline2\nline3\n";
        String newString = "line2_updated\n";
        
        try {
            java.nio.file.Files.writeString(tempDir.resolve(testFile), originalContent);
        } catch (java.io.IOException e) {
            testContext.failNow(e);
            return;
        }

        ToolCall editCall = new ToolCall("c1", "replace_in_file", Map.of(
            "file_path", testFile,
            "old_string", "line2\n",
            "new_string", newString
        ));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Editing file.", List.of(editCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Update line2 in main.py", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    try {
                        String finalContent = java.nio.file.Files.readString(tempDir.resolve(testFile));
                        assertTrue(finalContent.contains("line2_updated"));
                        testContext.completeNow();
                    } catch (java.io.IOException e) {
                        testContext.failNow(e);
                    }
                });
            }));
    }

    @Test
    void testApplyPatchWorkflow(Vertx vertx, VertxTestContext testContext) {
        String testFile = "app.java";
        String originalContent = "public class App {}\n";
        String patch = "--- app.java\n+++ app.java\n@@ -1,1 +1,1 @@\n-public class App {}\n+public class App { public static void main(String[] args) {} }\n";

        try {
            java.nio.file.Files.writeString(tempDir.resolve(testFile), originalContent);
        } catch (java.io.IOException e) {
            testContext.failNow(e);
            return;
        }

        ToolCall patchCall = new ToolCall("c1", "apply_patch", Map.of(
            "file_path", testFile,
            "patch", patch
        ));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Applying patch.", List.of(patchCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Apply patch to app.java", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    try {
                        String finalContent = java.nio.file.Files.readString(tempDir.resolve(testFile));
                        assertTrue(finalContent.contains("main"));
                        testContext.completeNow();
                    } catch (java.io.IOException e) {
                        testContext.failNow(e);
                    }
                });
            }));
    }
}
