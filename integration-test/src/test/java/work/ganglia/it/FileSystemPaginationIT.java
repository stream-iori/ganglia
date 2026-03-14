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
import work.ganglia.port.chat.Role;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class FileSystemPaginationIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private String testFilePath;
    private SessionContext baseContext;
    
    @TempDir
    Path sharedTempDir;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        testFilePath = sharedTempDir.resolve("large_file.txt").toString();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        vertx.fileSystem().writeFileBlocking(testFilePath, Buffer.buffer(sb.toString()));

        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(Future.failedFuture("Reflection disabled in tests"));

        // Mock Tool Call: Read first 5 lines
        ToolCall readCall = new ToolCall("call_1", "read_file", Map.of(
            "path", testFilePath,
            "offset", 0,
            "limit", 5
        ));

        ModelResponse response1 = new ModelResponse("Reading first part of the file.", List.of(readCall), new TokenUsage(10, 10));
        ModelResponse response2 = new ModelResponse("I have read the first 5 lines.", Collections.emptyList(), new TokenUsage(10, 10));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(response1))
            .thenReturn(Future.succeededFuture(response2));

        String projectRoot = sharedTempDir.toAbsolutePath().toString();

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)));

        Main.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                this.baseContext = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
                testContext.completeNow();
            }));
    }

    @Test
    void testPaginatedReadWorkflow(Vertx vertx, VertxTestContext testContext) {
        String sessionId = baseContext.sessionId();
        ganglia.agentLoop().run("Read the first 5 lines of large_file.txt", baseContext)
            .compose(response -> ganglia.sessionManager().getSession(sessionId))
            .onComplete(testContext.succeeding(resultContext -> {
                testContext.verify(() -> {
                    // Check if the output of the tool execution in context contains pagination info
                    boolean foundToolOutput = resultContext.history().stream()
                        .anyMatch(m -> m.role() == Role.TOOL && m.content() != null && m.content().contains("--- [Lines 0 to 5 of 20] ---"));

                    assertTrue(foundToolOutput, "Pagination metadata not found in history: " + resultContext.history());
                    testContext.completeNow();
                });
            }));
    }
}
