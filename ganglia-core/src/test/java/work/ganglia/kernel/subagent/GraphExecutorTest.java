package work.ganglia.kernel.subagent;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.stubs.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private AgentEnv env;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = vertx;
        this.modelGateway = new StubModelGateway();
        this.toolExecutor = new StubToolExecutor();
        this.configManager = new StubConfigManager(vertx);
        this.sessionManager = new DefaultSessionManager(new InMemoryStateEngine(), new InMemoryLogManager(), configManager);
        this.promptEngine = new StubPromptEngine();
        DefaultContextCompressor compressor = new DefaultContextCompressor(modelGateway, configManager);
        
        this.env = new AgentEnv(
            vertx, modelGateway, sessionManager, promptEngine,
            configManager, configManager, compressor, null,
            new DefaultObservationDispatcher(vertx), new ConsecutiveFailurePolicy(),
            new DefaultContextOptimizer(configManager, configManager, compressor, new TokenCounter())
        );

        this.graphExecutor = new DefaultGraphExecutor(env);

        DefaultAgentTaskFactory taskFactory = new DefaultAgentTaskFactory(
            env, toolExecutor, graphExecutor, null, null
        );
        env.setTaskFactory(taskFactory);
        graphExecutor.initialize(taskFactory);
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

        ModelOptions options = new ModelOptions(0.0, 1024, "test-model", true);
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
