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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class AgentLoopIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    
    @TempDir
    Path sharedTempDir;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        // Ensure background tasks like reflection don't crash with NPE
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(Future.failedFuture("Reflection disabled in tests"));

        String projectRoot = sharedTempDir.toAbsolutePath().toString();

        // Bootstrap with real components but mocked model
        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)));

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testFileConcatenation(Vertx vertx, VertxTestContext testContext) {
        // Prepare test files
        String fileA = sharedTempDir.resolve("a.txt").toString();
        String fileB = sharedTempDir.resolve("b.txt").toString();
        vertx.fileSystem().writeFileBlocking(fileA, Buffer.buffer("Hello"));
        vertx.fileSystem().writeFileBlocking(fileB, Buffer.buffer("World"));

        // Define expected sequence of tool calls
        ToolCall lsCall = new ToolCall("c1", "list_directory", Map.of("path", sharedTempDir.toString()));
        ToolCall readACall = new ToolCall("c2", "read_file", Map.of("path", fileA));
        ToolCall readBCall = new ToolCall("c3", "read_file", Map.of("path", fileB));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("I will list files.", List.of(lsCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("I see a.txt and b.txt. Reading a.txt.", List.of(readACall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Reading b.txt.", List.of(readBCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Final result is HelloWorld", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Concatenate files a.txt and b.txt in " + sharedTempDir, context)
            .onComplete(testContext.succeeding((String result) -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("HelloWorld"), "Result was: " + result);
                    testContext.completeNow();
                });
            }));

    }
}
