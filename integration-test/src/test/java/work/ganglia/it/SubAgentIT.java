package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.ganglia.Ganglia;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
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
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class SubAgentIT {

    private Ganglia ganglia;
    private ModelGateway mockModel;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext, @TempDir Path tempDir) {
        mockModel = mock(ModelGateway.class);
        when(mockModel.chat(any(ChatRequest.class))).thenReturn(Future.failedFuture("Reflection disabled"));

        String projectRoot = tempDir.toAbsolutePath().toString();

        BootstrapOptions options = BootstrapOptions.defaultOptions()
            .withProjectRoot(projectRoot)
            .withModelGateway(mockModel)
            .withOverrideConfig(new JsonObject().put("webui", new JsonObject().put("enabled", false)));

         CodingAgentBuilder.bootstrap(vertx, options)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testSubAgentDelegationAndReport(Vertx vertx, VertxTestContext testContext) {
        ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of("task", "SubTask"));

        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Delegating...", List.of(callSub), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("SubAgent Result", Collections.emptyList(), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Final Answer", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Run subtask", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("Final"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testRecursionProtection(Vertx vertx, VertxTestContext testContext) {
        // Start with a session already at level 3
        SessionContext context = new SessionContext("test-session", Collections.emptyList(), null, Map.of("sub_agent_level", 3), null, null);
        
        ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of("task", "SubTask"));
        
        when(mockModel.chatStream(any(ChatRequest.class), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("I will call subagent.", List.of(callSub), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Oops, recursion limit hit.", Collections.emptyList(), new TokenUsage(1, 1))));

        ganglia.agentLoop().run("Trigger recursion", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("recursion limit"));
                    testContext.completeNow();
                });
            }));
    }
}
