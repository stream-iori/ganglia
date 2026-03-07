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
public class CodeDiscoveryIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(), any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled in tests"));
        Main.bootstrap(vertx, ".ganglia/config.json", new JsonObject().put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testFullCodeDiscoveryWorkflow(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        String testFile = tempDir.resolve("discovery_test.java").toString();
        String content = "public class DiscoveryTest { public void hello() { System.out.println(\"SECRET_CODE_123\"); } }";
        vertx.fileSystem().writeFileBlocking(testFile, Buffer.buffer(content));

        // 1. Mock glob discovery
        ToolCall globCall = new ToolCall("c1", "glob", Map.of("path", tempDir.toString(), "pattern", "**/*.java"));

        // 2. Mock grep search
        ToolCall grepCall = new ToolCall("c2", "grep_search", Map.of("path", tempDir.toString(), "pattern", "SECRET_CODE_123"));

        when(mockModel.chatStream(any(), any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Finding files...", List.of(globCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Searching code...", List.of(grepCall), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Found the secret code in discovery_test.java", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Find the secret code 'SECRET_CODE_123' in " + tempDir, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("discovery_test.java"));
                    testContext.completeNow();
                });
            }));
    }
}
