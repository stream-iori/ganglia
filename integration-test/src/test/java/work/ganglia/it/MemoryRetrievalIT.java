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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class MemoryRetrievalIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));

        io.vertx.core.json.JsonObject configOverride = new io.vertx.core.json.JsonObject()
            .put("agent", new io.vertx.core.json.JsonObject().put("projectRoot", "/"));

        Main.bootstrap(vertx, ".ganglia/config.json", configOverride.put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testAgentRetrievesFromMemory(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String memoryFile = tempDir.resolve("MEMORY.md").toString();
        String secret = "The secret code is 998877";
        vertx.fileSystem().writeFileBlocking(memoryFile, Buffer.buffer(secret));

        // Mock Tool Calls: grep for secret in the memory file
        ToolCall grepCall = new ToolCall("c1", "grep_search", Map.of(
            "path", tempDir.toString(),
            "pattern", "secret code"
        ));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Searching memory...", List.of(grepCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("The code is 998877.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Find the secret code in " + tempDir, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("998877"));
                    testContext.completeNow();
                });
            }));
    }
}
