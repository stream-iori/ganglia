package work.ganglia.it;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import work.Main; 
import work.ganglia.Ganglia;
import work.ganglia.port.external.llm.ModelGateway;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.TokenUsage;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

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
    void setUp(Vertx vertx, VertxTestContext testContext) {
        mockModel = mock(ModelGateway.class);
        // Default behavior for background reflection
        when(mockModel.chat(any(), any(), any())).thenReturn(Future.failedFuture("Reflection disabled"));

        Main.bootstrap(vertx, ".ganglia/config.json", new JsonObject().put("webui", new JsonObject().put("enabled", false)), mockModel)
            .onComplete(testContext.succeeding((Ganglia g) -> {
                this.ganglia = g;
                testContext.completeNow();
            }));
    }

    @Test
    void testSubAgentDelegationAndReport(Vertx vertx, VertxTestContext testContext) {
        // 1. Parent decides to call sub-agent
        ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of(
            "task", "Analyze the current directory",
            "persona", "INVESTIGATOR"
        ));

        // 2. Mock Model responses
        // First call: Parent wants a sub-agent
        // Second call (Sub-Agent loop): Returns final answer immediately
        // Third call: Parent receives report and gives final answer
        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Delegating...", List.of(callSub), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("The directory contains many Java files.", Collections.emptyList(), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Investigation finished.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Analyze codebase", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("finished"));
                    // Verify that the child loop was actually run by checking the captor or interactions
                    verify(mockModel, times(3)).chatStream(any(), any(), any(), any());
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testInvestigatorToolFiltering(Vertx vertx, VertxTestContext testContext) {
        // We want to verify that when a sub-agent is INVESTIGATOR, it cannot see 'write_file'

        ToolCall callSub = new ToolCall("c1", "call_sub_agent", Map.of(
            "task", "Try to write a file",
            "persona", "INVESTIGATOR"
        ));

        ArgumentCaptor<List<ToolDefinition>> toolsCaptor = ArgumentCaptor.forClass(List.class);

        when(mockModel.chatStream(any(), toolsCaptor.capture(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Delegating...", List.of(callSub), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("I can't write.", Collections.emptyList(), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Report: Investigator lacks write permissions.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Try writing via sub-agent", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    // Check the tools provided to the sub-agent (the 2nd interaction)
                    List<List<ToolDefinition>> allCapturedTools = toolsCaptor.getAllValues();
                    List<ToolDefinition> subAgentTools = allCapturedTools.get(1);

                    boolean hasWrite = subAgentTools.stream().anyMatch(t -> t.name().equals("write_file") || t.name().equals("replace_in_file"));
                    assertFalse(hasWrite, "INVESTIGATOR persona should not have access to file modification tools.");

                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testRecursionProtection(Vertx vertx, VertxTestContext testContext) {
        // Mock a sub-agent that tries to call ANOTHER sub-agent
        ToolCall callSub1 = new ToolCall("c1", "call_sub_agent", Map.of("task", "Level 1"));
        ToolCall callSub2 = new ToolCall("c2", "call_sub_agent", Map.of("task", "Level 2"));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Go to L1", List.of(callSub1), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Try going to L2", List.of(callSub2), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Final answer", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Test recursion", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    // The result of the second call should be an error report from the tool
                    // Because we mock the model to return a tool call, and the loop executes it.
                    // The loop will see the error from SubAgentTask scheduling and feed it back to the model.

                    // We can check if the model was called with an observation containing RECURSION_LIMIT
                    verify(mockModel, atLeastOnce()).chatStream(argThat(msgs ->
                        msgs.stream().anyMatch(m -> m.content() != null && m.content().contains("RECURSION_LIMIT"))
                    ), any(), any(), any());

                    testContext.completeNow();
                });
            }));
    }
}
