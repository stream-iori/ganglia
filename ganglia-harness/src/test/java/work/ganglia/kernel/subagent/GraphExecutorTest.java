package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.ganglia.kernel.BaseKernelTest;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.external.llm.ModelResponse;

public class GraphExecutorTest extends BaseKernelTest {

  private DefaultGraphExecutor graphExecutor;

  @BeforeEach
  void setUpExecutor() {
    AgentLoopFactory loopFactory = () -> loop;
    this.graphExecutor = new DefaultGraphExecutor(loopFactory);
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(loopFactory, tools, graphExecutor, null, null);
    graphExecutor.initialize(taskFactory);
  }

  @Test
  void testParallelAndSequentialExecution(VertxTestContext testContext) {
    // Prepare graph: 1 & 2 parallel, 3 depends on 1 & 2
    TaskNode n1 = new TaskNode("node1", "Task 1", "INVESTIGATOR", Collections.emptyList(), null);
    TaskNode n2 = new TaskNode("node2", "Task 2", "INVESTIGATOR", Collections.emptyList(), null);
    TaskNode n3 =
        new TaskNode("node3", "Task 3 (Summary)", "REFACTORER", List.of("node1", "node2"), null);

    TaskGraph graph = new TaskGraph(List.of(n1, n2, n3));

    // Mock responses for sub-agents
    model.addResponse(new ModelResponse("Report from Task 1", Collections.emptyList(), null));
    model.addResponse(new ModelResponse("Report from Task 2", Collections.emptyList(), null));
    model.addResponse(new ModelResponse("Final Aggregated Report", Collections.emptyList(), null));

    ModelOptions options = new ModelOptions(0.0, 1024, "test-model", true);
    SessionContext parentContext =
        new SessionContext(
            "parent-session", null, null, Map.of("sub_agent_level", 0), null, options);

    graphExecutor
        .execute(graph, parentContext)
        .onComplete(
            testContext.succeeding(
                report -> {
                  testContext.verify(
                      () -> {
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
