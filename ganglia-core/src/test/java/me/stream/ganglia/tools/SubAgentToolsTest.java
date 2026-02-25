package me.stream.ganglia.tools;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.stubs.*;
import me.stream.ganglia.tools.model.ToolCall;
import me.stream.ganglia.tools.model.ToolInvokeResult;
import me.stream.ganglia.tools.subagent.GraphExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(VertxExtension.class)
public class SubAgentToolsTest {

    private Vertx vertx;
    private SubAgentTools subAgentTools;
    private GraphExecutor graphExecutor;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.graphExecutor = Mockito.mock(GraphExecutor.class);
        
        StubModelGateway modelGateway = new StubModelGateway();
        StubConfigManager configManager = new StubConfigManager(vertx);
        me.stream.ganglia.memory.ContextCompressor compressor = new me.stream.ganglia.memory.ContextCompressor(modelGateway, configManager);
        subAgentTools = new SubAgentTools(
            vertx,
            modelGateway,
            null, // sessionManager
            null, // promptEngine
            configManager,
            null, // toolExecutor
            graphExecutor,
            compressor
        );
    }

    @Test
    void testProposeTaskGraphInterrupt(VertxTestContext testContext) {
        Map<String, Object> args = Map.of(
            "nodes", List.of(
                Map.of("id", "n1", "task", "Task 1")
            )
        );
        ToolCall call = new ToolCall("call1", "propose_task_graph", args);
        SessionContext context = new SessionContext("s1", null, null, Map.of("sub_agent_level", 0), null, null, null);

        subAgentTools.execute(call, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.INTERRUPT, result.status());
                    assertTrue(result.output().contains("PROPOSED TASK GRAPH"));
                    assertTrue(result.output().contains("Task 1"));
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testProposeTaskGraphExecution(VertxTestContext testContext) {
        Map<String, Object> args = Map.of(
            "nodes", List.of(
                Map.of("id", "n1", "task", "Task 1")
            ),
            "approved", true
        );
        ToolCall call = new ToolCall("call1", "propose_task_graph", args);
        SessionContext context = new SessionContext("s1", null, null, Map.of("sub_agent_level", 0), null, null, null);

        when(graphExecutor.execute(any(), any())).thenReturn(io.vertx.core.Future.succeededFuture("Graph execution finished"));

        subAgentTools.execute(call, context)
            .onComplete(testContext.succeeding(result -> {
                testContext.verify(() -> {
                    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
                    assertEquals("Graph execution finished", result.output());
                    testContext.completeNow();
                });
            }));
    }
}
