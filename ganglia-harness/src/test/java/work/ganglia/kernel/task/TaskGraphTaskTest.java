package work.ganglia.kernel.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.subagent.GraphExecutor;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.stubs.StubExecutionContext;

@ExtendWith(VertxExtension.class)
class TaskGraphTaskTest extends BaseGangliaTest {

  private List<Map<String, Object>> sampleNodes() {
    Map<String, Object> node1 = new HashMap<>();
    node1.put("id", "t1");
    node1.put("task", "List files");
    node1.put("persona", "CODER");
    node1.put("dependencies", List.of());

    Map<String, Object> node2 = new HashMap<>();
    node2.put("id", "t2");
    node2.put("task", "Summarize results");
    node2.put("persona", "GENERAL");
    node2.put("dependencies", List.of("t1"));

    return List.of(node1, node2);
  }

  private GraphExecutor successExecutor(String report) {
    return (graph, ctx) -> Future.succeededFuture(report);
  }

  private GraphExecutor failingExecutor(String errorMsg) {
    return (graph, ctx) -> Future.failedFuture(new RuntimeException(errorMsg));
  }

  @Test
  void testNotApprovedReturnsInterrupt(VertxTestContext testContext) {
    Map<String, Object> args = new HashMap<>();
    args.put("approved", false);
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c1", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("done"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.INTERRUPT, result.status());
                        assertTrue(result.output().contains("PROPOSED TASK GRAPH"));
                        assertTrue(result.output().contains("t1"));
                        assertTrue(result.output().contains("t2"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testApprovedExecutesGraph(VertxTestContext testContext) {
    Map<String, Object> args = new HashMap<>();
    args.put("approved", true);
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c2", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("Graph complete: t1, t2"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        assertTrue(result.output().contains("Graph complete"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testGraphExecutionFailureRecovered(VertxTestContext testContext) {
    Map<String, Object> args = new HashMap<>();
    args.put("approved", true);
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c3", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, failingExecutor("executor failed"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("GRAPH_EXECUTION_ERROR"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRecursionLimitReached(VertxTestContext testContext) {
    Map<String, Object> meta = new HashMap<>();
    meta.put("sub_agent_level", 1);
    SessionContext deepContext = createSessionContext().withMetadata(meta);

    Map<String, Object> args = new HashMap<>();
    args.put("approved", true);
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c4", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("should not run"));

    task.execute(deepContext, new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.ERROR, result.status());
                        assertTrue(result.output().contains("RECURSION_LIMIT"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testIdAndName() {
    Map<String, Object> args = new HashMap<>();
    args.put("approved", false);
    args.put("nodes", List.of());
    ToolCall call = new ToolCall("c5", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("ok"));

    assertEquals("c5", task.id());
    assertEquals("task_graph", task.name());
    assertEquals(call, task.getToolCall());
  }

  @Test
  void testApprovedStringParsingTrue(VertxTestContext testContext) {
    // approved as String "true"
    Map<String, Object> args = new HashMap<>();
    args.put("approved", "true");
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c6", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("Parsed true"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testInterruptContainsDependencies(VertxTestContext testContext) {
    // Node with dependencies should mention them in interrupt output
    Map<String, Object> args = new HashMap<>();
    args.put("approved", false);
    args.put("nodes", sampleNodes());
    ToolCall call = new ToolCall("c7", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("ok"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.output().contains("Depends on: t1"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testNodesWithoutPersonaDefaultsToGeneral(VertxTestContext testContext) {
    Map<String, Object> node = new HashMap<>();
    node.put("id", "n1");
    node.put("task", "Simple task");
    // No persona key

    Map<String, Object> args = new HashMap<>();
    args.put("approved", true);
    args.put("nodes", List.of(node));
    ToolCall call = new ToolCall("c8", "task_graph", args);
    TaskGraphTask task = new TaskGraphTask(call, successExecutor("node done"));

    task.execute(createSessionContext(), new StubExecutionContext())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
                        testContext.completeNow();
                      });
                }));
  }
}
