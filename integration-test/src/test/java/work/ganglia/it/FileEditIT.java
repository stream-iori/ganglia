package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main;
import work.ganglia.core.Ganglia;
import work.ganglia.core.llm.ModelGateway;
import work.ganglia.core.model.ModelResponse;
import work.ganglia.core.model.SessionContext;
import work.ganglia.core.model.TokenUsage;
import work.ganglia.tools.model.ToolCall;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class FileEditIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private String testFilePath;
    private SessionContext baseContext;

    @BeforeEach
    void setUp(Vertx vertx, @TempDir Path tempDir, VertxTestContext testContext) {
        testFilePath = tempDir.resolve("app.py").toString();
        String initialContent = "def hello():\n    print(\"Hello World\")\n\nif __name__ == \"__main__\":\n    hello()\n";
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(initialContent));

        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));

        // 1. Mock first LLM call: Decide to use replace_in_file
        ToolCall replaceCall = new ToolCall("call_1", "replace_in_file", Map.of(
            "file_path", testFilePath,
            "old_string", "    print(\"Hello World\")",
            "new_string", "    print(\"Hello Ganglia\")"
        ));

        ModelResponse response1 = new ModelResponse("I will update the print statement.", List.of(replaceCall), new TokenUsage(1, 1));

        // 2. Mock second LLM call: Final answer
        ModelResponse response2 = new ModelResponse("Updated successfully.", Collections.emptyList(), new TokenUsage(1, 1));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(response1))
            .thenReturn(Future.succeededFuture(response2));

        Main.bootstrap(vertx, ".ganglia/config.json", null, mockModel)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                this.baseContext = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
                testContext.completeNow();
            }));
    }

    @Test
    void testSurgicalEditWorkflow(Vertx vertx, VertxTestContext testContext) {
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
}
