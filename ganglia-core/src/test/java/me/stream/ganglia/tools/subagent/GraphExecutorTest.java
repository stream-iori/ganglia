package me.stream.ganglia.tools.subagent;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.llm.ModelGateway;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.ModelResponse;
import me.stream.ganglia.core.model.SessionContext;
import me.stream.ganglia.core.session.DefaultSessionManager;
import me.stream.ganglia.core.session.SessionManager;
import me.stream.ganglia.stubs.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class GraphExecutorTest {

    private Vertx vertx;
    private StubModelGateway modelGateway;
    private StubToolExecutor toolExecutor;
    private SessionManager sessionManager;
    private StubPromptEngine promptEngine;
    private StubConfigManager configManager;
    private DefaultGraphExecutor graphExecutor;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.modelGateway = new StubModelGateway();
        this.toolExecutor = new StubToolExecutor();
        this.configManager = new StubConfigManager(vertx);
        this.sessionManager = new DefaultSessionManager(new InMemoryStateEngine(), new InMemoryLogManager(), configManager);
        this.promptEngine = new StubPromptEngine();
        this.graphExecutor = new DefaultGraphExecutor(vertx, modelGateway, toolExecutor, sessionManager, promptEngine, configManager);
    }

    @Test
    void testParallelAndSequentialExecution(VertxTestContext testContext) {
        // Prepare graph: 1 & 2 parallel, 3 depends on 1 & 2
        TaskNode n1 = new TaskNode("node1", "Task 1", "INVESTIGATOR", Collections.emptyList(), null);
        TaskNode n2 = new TaskNode("node2", "Task 2", "INVESTIGATOR", Collections.emptyList(), null);
        TaskNode n3 = new TaskNode("node3", "Task 3 (Summary)", "REFACTORER", List.of("node1", "node2"), null);
        
        TaskGraph graph = new TaskGraph(List.of(n1, n2, n3));
        
        // Mock responses for sub-agents
        modelGateway.addResponse(new ModelResponse("Report from Task 1", Collections.emptyList(), null));
        modelGateway.addResponse(new ModelResponse("Report from Task 2", Collections.emptyList(), null));
        modelGateway.addResponse(new ModelResponse("Final Aggregated Report", Collections.emptyList(), null));

        ModelOptions options = new ModelOptions(0.0, 1024, "test-model");
        SessionContext parentContext = new SessionContext("parent-session", null, null, Map.of("sub_agent_level", 0), null, options, null);

        graphExecutor.execute(graph, parentContext)
            .onComplete(testContext.succeeding(report -> {
                testContext.verify(() -> {
                    assertTrue(report.contains("NODE ID: node1"));
                    assertTrue(report.contains("NODE ID: node2"));
                    assertTrue(report.contains("NODE ID: node3"));
                    assertTrue(report.contains("Report from Task 1"));
                    assertTrue(report.contains("Report from Task 2"));
                    assertTrue(report.contains("Final Aggregated Report"));
                    testContext.completeNow();
                });
            }));
    }
}
