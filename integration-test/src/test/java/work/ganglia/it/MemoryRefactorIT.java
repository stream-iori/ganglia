package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ChatRequest;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.TokenUsage;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import work.ganglia.coding.CodingAgentBuilder;

@ExtendWith(VertxExtension.class)
public class MemoryRefactorIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;
    private String projectRoot;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        mockModel = mock(ModelGateway.class);
        projectRoot = tempDir.toAbsolutePath().toString();

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject()
                .put("webui", new JsonObject().put("enabled", false)));

        CodingAgentBuilder.bootstrap(vertx, options)
            .onComplete(testContext.succeeding(g -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testObservationCompressionAndRecall(Vertx vertx, VertxTestContext testContext) {
        String longOutput = "This is a very long output that should be compressed. ".repeat(200); // Definitely > 4000
        
        // 1. Agent calls a tool (mocking a tool call that returns long output)
        // Since we want to test the interceptor in StandardToolTask, we need a real tool call.
        // Let's mock the tool executor or use a real one like bash if possible, 
        // but here it's easier to mock the LLM response to trigger a tool call.

        ToolCall longToolCall = new ToolCall("c1", "bash", Map.of("command", "echo 'long output'"));
        
        // Mock LLM interaction: 
        // Turn 1: LLM calls bash
        // Turn 2: LLM receives compressed summary, then calls recall_memory
        // Turn 3: LLM finishes
        
        when(mockModel.chatStream(any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("", List.of(longToolCall), new TokenUsage(1, 1))))
            .thenAnswer(invocation -> {
                ChatRequest req = invocation.getArgument(0);
                String lastMessage = req.messages().get(req.messages().size() - 1).content();
                assertTrue(lastMessage.contains("compressed"), "Last message should mention compression");
                assertTrue(lastMessage.contains("ID:"), "Last message should contain a recall ID");
                
                // Extract ID (e.g., ID: abcdef12)
                String id = lastMessage.split("ID: ")[1].split("\\.")[0].trim();
                ToolCall recallCall = new ToolCall("c2", "recall_memory", Map.of("id", id));
                return Future.succeededFuture(new ModelResponse("", List.of(recallCall), new TokenUsage(1, 1)));
            })
            .thenAnswer(invocation -> {
                ChatRequest req = invocation.getArgument(0);
                String lastMessage = req.messages().get(req.messages().size() - 1).content();
                assertTrue(lastMessage.contains("Full content"), "Should have recalled full content");
                return Future.succeededFuture(new ModelResponse("Final Answer with full content info.", Collections.emptyList(), new TokenUsage(1, 1)));
            });

        // Mock the LLM call used for compression itself (LLMObservationCompressor)
        when(mockModel.chat(any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Summary: Found long text.", Collections.emptyList(), new TokenUsage(5, 5))));

        // We need to make sure 'bash' tool returns the long output. 
        // Since we use the real ToolExecutor but mock the LLM, we should probably mock the tool output if possible, 
        // but StandardToolTask calls toolExecutor.execute().
        // For simplicity, let's just use a fake tool that returns long output by overriding the ToolExecutor if we could, 
        // but it's easier to just mock the file system if we used a file tool.
        
        // Actually, let's just mock the bash command execution via the tool executor if we can? 
        // No, it's easier to just create a file and cat it.
        try {
            vertx.fileSystem().writeFileBlocking(projectRoot + "/long.txt", io.vertx.core.buffer.Buffer.buffer(longOutput));
        } catch (Exception e) {
            testContext.failNow(e);
            return;
        }
        
        ToolCall readFileCall = new ToolCall("c1", "read_file", Map.of("path", projectRoot + "/long.txt"));
        when(mockModel.chatStream(any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("", List.of(readFileCall), new TokenUsage(1, 1))))
            .thenAnswer(invocation -> {
                ChatRequest req = invocation.getArgument(0);
                String lastMessage = req.messages().get(req.messages().size() - 1).content();
                assertTrue(lastMessage.contains("ID: "));
                String id = lastMessage.split("ID: ")[1].split("\\.")[0].trim();
                return Future.succeededFuture(new ModelResponse("", List.of(new ToolCall("c2", "recall_memory", Map.of("id", id))), new TokenUsage(1, 1)));
            })
            .thenReturn(Future.succeededFuture(new ModelResponse("Done.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());
        ganglia.agentLoop().run("Read long.txt", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    testContext.completeNow();
                });
            }));
    }
}