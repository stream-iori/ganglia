package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.Ganglia;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class FileEditIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private String testFilePath;
    private String testFileName = "app.py";
    private SessionContext baseContext;

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir, VertxTestContext testContext) {
        testFilePath = tempDir.resolve(testFileName).toString();
        String initialContent = "def hello():\n    print(\"Hello World\")\n\nif __name__ == \"__main__\":\n    hello()\n";
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(initialContent));

        mockModel = mock(ModelGateway.class);
        
        JsonObject overrideConfig = new JsonObject()
            .put("webui", new JsonObject().put("enabled", false))
            .put("agent", new JsonObject().put("projectRoot", tempDir.toString()));

        Main.bootstrap(vertx, ".ganglia/config.json", overrideConfig, mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                this.baseContext = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
                testContext.completeNow();
            }));
    }

    @Test
    void testSurgicalEditWorkflow(Vertx vertx, VertxTestContext testContext) {
        ToolCall replaceCall = new ToolCall("call_1", "replace_in_file", Map.of(
            "file_path", testFileName,
            "old_string", "    print(\"Hello World\")",
            "new_string", "    print(\"Hello Ganglia\")"
        ));

        when(mockModel.chatStream(any(), any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("I will update the print statement.", List.of(replaceCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Updated successfully.", Collections.emptyList(), new TokenUsage(1, 1))));

        ganglia.agentLoop().run("Change 'Hello World' to 'Hello Ganglia' in app.py", baseContext)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    String updatedContent = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertTrue(updatedContent.contains("print(\"Hello Ganglia\")"));
                    assertTrue(!updatedContent.contains("Hello World"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testWriteFileWorkflow(Vertx vertx, VertxTestContext testContext) {
        String newContent = "print('New Content Overwritten')";
        ToolCall writeCall = new ToolCall("call_w1", "write_file", Map.of(
            "file_path", testFileName,
            "content", newContent
        ));

        when(mockModel.chatStream(any(), any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Overwriting file.", List.of(writeCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        ganglia.agentLoop().run("Overwrite app.py with new content", baseContext)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    String updatedContent = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertEquals(newContent, updatedContent);
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testApplyPatchWorkflow(Vertx vertx, VertxTestContext testContext) {
        String patch = """
            --- app.py
            +++ app.py
            @@ -1,5 +1,5 @@
             def hello():
            -    print("Hello World")
            +    print("Hello Patch")
             
             if __name__ == "__main__":
                 hello()
            """;
        
        ToolCall patchCall = new ToolCall("call_p1", "apply_patch", Map.of(
            "file_path", testFileName,
            "patch", patch
        ));

        when(mockModel.chatStream(any(), any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Applying patch.", List.of(patchCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        ganglia.agentLoop().run("Apply patch to app.py", baseContext)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    String updatedContent = vertx.fileSystem().readFileBlocking(testFilePath).toString();
                    assertTrue(updatedContent.contains("print(\"Hello Patch\")"));
                    testContext.completeNow();
                });
            }));
    }
}
