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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
public class SubAgentGraphIT {

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
    void testTaskGraphOrchestration(Vertx vertx, VertxTestContext testContext) {
        // 1. Parent proposes a graph
        Map<String, Object> node1 = Map.of("id", "n1", "task", "Investigate Component A", "persona", "INVESTIGATOR");
        Map<String, Object> node2 = Map.of("id", "n2", "task", "Investigate Component B", "persona", "INVESTIGATOR");
        Map<String, Object> node3 = Map.of("id", "n3", "task", "Synthesize findings", "persona", "GENERAL", "dependencies", List.of("n1", "n2"));

        ToolCall proposeGraph = new ToolCall("p1", "propose_task_graph", Map.of(
            "nodes", List.of(node1, node2, node3)
        ));

        // 2. Mock Model responses
        // First call: Parent proposes graph
        // Second call: (After approval interrupt) Parent receives the aggregated report and finishes

        // Response for Sub-Agent Node 1
        ModelResponse res1 = new ModelResponse("Findings for A: All good.", Collections.emptyList(), new TokenUsage(1, 1));
        // Response for Sub-Agent Node 2
        ModelResponse res2 = new ModelResponse("Findings for B: Bug found in sync.", Collections.emptyList(), new TokenUsage(1, 1));
        // Response for Sub-Agent Node 3
        ModelResponse res3 = new ModelResponse("Summary: Bug in B needs fixing, A is fine.", Collections.emptyList(), new TokenUsage(1, 1));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("I have a plan.", List.of(proposeGraph), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(res1)) // n1
            .thenReturn(Future.succeededFuture(res2)) // n2
            .thenReturn(Future.succeededFuture(res3)) // n3
            .thenReturn(Future.succeededFuture(new ModelResponse("Graph complete. Here is the summary: ...", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        // Run first part - should interrupt
        ganglia.agentLoop().run("Improve the system components.", context)
            .onComplete(testContext.succeeding(interruptOutput -> {
                testContext.verify(() -> {
                    assertTrue(interruptOutput.contains("PROPOSED TASK GRAPH"));
                    assertTrue(interruptOutput.contains("Investigate Component A"));

                    // Simulate user approval
                    SessionContext nextContext = ganglia.sessionManager().getSession(context.sessionId()).result();
                    // We need to add 'approved: true' to the tool arguments in history
                    // But wait, the loop resume logic expects the OUTPUT of the tool call.
                    // TaskGraphTask logic: if !approved -> interrupt. resume with "y" (user output).
                    // BUT our ToolInvokeResult for interrupt doesn't automatically update arguments.
                    // The user interaction should provide the "approved: true" value if they confirm.
                    // Actually, the Standard loop resume logic takes the tool output.
                    // TaskGraphTask.proposeTaskGraph doesn't use the 'toolOutput' to update its internal 'approved' state yet.

                    // Wait, I designed it so that the user provides feedback which is fed back to the model.
                    // But for Sub-Agents, we want the TOOL itself to proceed once approved.
                    // In current implementation of TaskGraphTask:
                    // if (!approved) return interrupt;
                    // If the user says 'y', the model should call 'propose_task_graph' AGAIN with 'approved: true'.

                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testTaskGraphExecutionAfterApproval(Vertx vertx, VertxTestContext testContext) {
        // Prepare graph
        Map<String, Object> node1 = Map.of("id", "n1", "task", "Task 1", "persona", "INVESTIGATOR");

        ToolCall approvedGraph = new ToolCall("p1", "propose_task_graph", Map.of(
            "nodes", List.of(node1),
            "approved", true
        ));

        when(mockModel.chatStream(any(), any(), any(), any()))
            .thenReturn(Future.succeededFuture(new ModelResponse("Executing...", List.of(approvedGraph), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Node 1 done.", Collections.emptyList(), new TokenUsage(1, 1))))
            .thenReturn(Future.succeededFuture(new ModelResponse("Everything finished.", Collections.emptyList(), new TokenUsage(1, 1))));

        SessionContext context = ganglia.sessionManager().createSession(UUID.randomUUID().toString());

        ganglia.agentLoop().run("Run the graph", context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertTrue(result.contains("finished"));
                    // Verify that chatStream was called for:
                    // 1. Parent thought -> ToolCall
                    // 2. Sub-agent node 1 loop
                    // 3. Parent final answer
                    verify(mockModel, times(3)).chatStream(any(), any(), any(), any());
                    testContext.completeNow();
                });
            }));
    }
}
