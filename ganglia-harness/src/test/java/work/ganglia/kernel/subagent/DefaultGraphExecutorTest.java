package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;

@ExtendWith(VertxExtension.class)
class DefaultGraphExecutorTest extends BaseGangliaTest {

  /** Captures the prompt passed to each child loop, keyed by session ID suffix. */
  private final ConcurrentHashMap<String, String> capturedPrompts = new ConcurrentHashMap<>();

  private DefaultGraphExecutor executor;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    capturedPrompts.clear();

    AgentLoopFactory loopFactory =
        () ->
            new AgentLoop() {
              @Override
              public Future<String> run(String userInput, SessionContext context) {
                // Extract node id from session id (format: "parent-session-node-{id}")
                String sessionId = context.sessionId();
                capturedPrompts.put(sessionId, userInput);
                return Future.succeededFuture("Result from " + sessionId);
              }

              @Override
              public Future<String> run(
                  String userInput,
                  SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return run(userInput, context);
              }

              @Override
              public Future<String> resume(
                  String askId,
                  String toolOutput,
                  SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return Future.succeededFuture("resumed");
              }

              @Override
              public void stop(String sessionId) {}
            };

    executor = new DefaultGraphExecutor(loopFactory);
  }

  @Test
  void executeNode_withMissionContext_prependsMissionToPrompt(VertxTestContext testContext) {
    TaskNode node =
        new TaskNode(
            "n1",
            "Investigate issue",
            "INVESTIGATOR",
            List.of(),
            null,
            "Fix the critical login bug",
            ExecutionMode.SELF,
            IsolationLevel.NONE);

    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent-session");

    assertFutureSuccess(
        executor.execute(graph, ctx),
        testContext,
        report -> {
          String prompt = capturedPrompts.get("parent-session-node-n1");
          assertNotNull(prompt, "Child loop should have been called");
          assertTrue(
              prompt.startsWith("MISSION: Fix the critical login bug"),
              "Prompt should start with MISSION, got: " + prompt);
          assertTrue(prompt.contains("TASK: Investigate issue"), "Prompt should contain TASK");
        });
  }

  @Test
  void executeNode_withoutMissionContext_usesTaskOnly(VertxTestContext testContext) {
    TaskNode node = new TaskNode("n1", "Simple task", "GENERAL", List.of(), null);

    TaskGraph graph = new TaskGraph(List.of(node));
    SessionContext ctx = createSessionContext("parent-session");

    assertFutureSuccess(
        executor.execute(graph, ctx),
        testContext,
        report -> {
          String prompt = capturedPrompts.get("parent-session-node-n1");
          assertNotNull(prompt);
          assertTrue(prompt.startsWith("TASK: Simple task"), "Prompt should start with TASK");
          assertFalse(prompt.contains("MISSION:"), "Should not contain MISSION prefix");
        });
  }

  @Test
  void executeNode_withInputMapping_onlyMappedDependencyResults(VertxTestContext testContext) {
    TaskNode dep1 = new TaskNode("dep1", "Research A", "INVESTIGATOR", List.of(), null);
    TaskNode dep2 = new TaskNode("dep2", "Research B", "INVESTIGATOR", List.of(), null);
    // n1 depends on both but only maps dep1's output as "context"
    TaskNode n1 =
        new TaskNode(
            "n1",
            "Synthesize",
            "GENERAL",
            List.of("dep1", "dep2"),
            Map.of("context", "dep1"),
            null,
            ExecutionMode.SELF,
            IsolationLevel.NONE);

    TaskGraph graph = new TaskGraph(List.of(dep1, dep2, n1));
    SessionContext ctx = createSessionContext("parent-session");

    assertFutureSuccess(
        executor.execute(graph, ctx),
        testContext,
        report -> {
          String prompt = capturedPrompts.get("parent-session-node-n1");
          assertNotNull(prompt);
          // Should contain mapped key "context" with dep1's result
          assertTrue(prompt.contains("context"), "Should contain mapped key 'context'");
          assertTrue(
              prompt.contains("Result from parent-session-node-dep1"),
              "Should contain dep1 result");
          // Should NOT contain dep2's result since it's not in inputMapping
          assertFalse(
              prompt.contains("dep2"), "Should not contain dep2 since it's not in inputMapping");
        });
  }

  @Test
  void executeNode_withoutInputMapping_includesAllDependencyResults(VertxTestContext testContext) {
    TaskNode dep1 = new TaskNode("dep1", "Research A", "INVESTIGATOR", List.of(), null);
    TaskNode dep2 = new TaskNode("dep2", "Research B", "INVESTIGATOR", List.of(), null);
    TaskNode n1 = new TaskNode("n1", "Synthesize", "GENERAL", List.of("dep1", "dep2"), null);

    TaskGraph graph = new TaskGraph(List.of(dep1, dep2, n1));
    SessionContext ctx = createSessionContext("parent-session");

    assertFutureSuccess(
        executor.execute(graph, ctx),
        testContext,
        report -> {
          String prompt = capturedPrompts.get("parent-session-node-n1");
          assertNotNull(prompt);
          assertTrue(
              prompt.contains("dep1") && prompt.contains("dep2"),
              "Should contain both dependency IDs");
        });
  }
}
