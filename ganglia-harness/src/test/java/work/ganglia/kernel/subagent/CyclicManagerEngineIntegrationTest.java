package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.subagent.CyclicManagerEngine.EngineConfig;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;

/**
 * End-to-end integration test: multi-cycle convergence scenario. Uses real InMemoryBlackboard and
 * DefaultTerminationController with a mock GraphExecutor that produces different results per cycle.
 */
@ExtendWith(VertxExtension.class)
class CyclicManagerEngineIntegrationTest extends BaseGangliaTest {

  private final CopyOnWriteArrayList<ObservationType> dispatchedTypes =
      new CopyOnWriteArrayList<>();
  private ObservationDispatcher captureDispatcher;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    dispatchedTypes.clear();

    captureDispatcher =
        new ObservationDispatcher() {
          @Override
          public void dispatch(String sessionId, ObservationType type, String content) {
            dispatchedTypes.add(type);
          }

          @Override
          public void dispatch(
              String sessionId, ObservationType type, String content, Map<String, Object> data) {
            dispatchedTypes.add(type);
          }
        };
  }

  @Test
  void multiCycle_convergesAfterTwoCycles(VertxTestContext testContext) {
    // Blackboard shared across cycles
    InMemoryBlackboard blackboard = new InMemoryBlackboard(captureDispatcher, "test-session");

    // Controller: converge on validation pass, stall after 3 cycles with no new facts
    DefaultTerminationController controller = new DefaultTerminationController(blackboard, 5, 3);

    AtomicInteger cycleCount = new AtomicInteger(0);

    // Mock executor: cycle 1 publishes partial fact, cycle 2 publishes complete fact
    GraphExecutor mockExecutor =
        (graph, parentContext) -> {
          int cycle = cycleCount.incrementAndGet();
          if (cycle == 1) {
            // Cycle 1: publish partial result, validation will fail
            return blackboard
                .publish("node-1", "Partial analysis: found 3 issues", null, cycle)
                .map(
                    fact ->
                        "Cycle 1 report: partial analysis completed, 3 issues remain unresolved");
          } else {
            // Cycle 2: supersede old fact, publish complete result
            return blackboard
                .getActiveFacts()
                .compose(
                    active -> {
                      Future<Void> supersedeFuture = Future.succeededFuture();
                      for (var fact : active) {
                        supersedeFuture =
                            supersedeFuture.compose(
                                v -> blackboard.supersede(fact.id(), "resolved"));
                      }
                      return supersedeFuture;
                    })
                .compose(v -> blackboard.publish("node-1", "All 3 issues resolved", null, cycle))
                .map(fact -> "Cycle 2 report: all issues resolved, tests passing");
          }
        };

    // Controller sees no worktrees, so MergeGateResult is null.
    // We need a custom termination controller that checks blackboard state for convergence.
    // Since DefaultTerminationController checks validationPassed from CycleResult,
    // and CycleResult is determined by MergeGate (null = false when no worktrees),
    // we use a custom controller that evaluates convergence via blackboard state.
    TerminationController smartController =
        (cycleNumber, cycleResult) -> {
          // Check if all active facts indicate "resolved"
          return blackboard
              .getActiveFacts()
              .map(
                  activeFacts -> {
                    boolean allResolved =
                        activeFacts.stream().allMatch(f -> f.summary().contains("resolved"));

                    if (allResolved && !activeFacts.isEmpty()) {
                      return new TerminationController.Decision(
                          DecisionType.CONVERGED, "All issues resolved");
                    }
                    if (cycleNumber >= 5) {
                      return new TerminationController.Decision(
                          DecisionType.BUDGET_EXCEEDED, "Max cycles reached");
                    }
                    return new TerminationController.Decision(
                        DecisionType.CONTINUE, "Issues remain");
                  });
        };

    CyclicManagerEngine engine =
        new CyclicManagerEngine(
            mockExecutor, blackboard, smartController, captureDispatcher, new EngineConfig(5));

    TaskGraph graph =
        new TaskGraph(List.of(new TaskNode("n1", "Analyze and fix", "GENERAL", List.of(), null)));

    SessionContext ctx = createSessionContext("test-session");

    engine
        .run(graph, ctx)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          // Should converge after 2 cycles
                          assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
                          assertEquals(2, result.totalCycles());
                          assertEquals(2, result.cycleReports().size());
                          assertTrue(result.cycleReports().get(0).contains("partial"));
                          assertTrue(result.cycleReports().get(1).contains("resolved"));

                          // Verify observation events dispatched
                          assertEquals(
                              2,
                              dispatchedTypes.stream()
                                  .filter(t -> t == ObservationType.MANAGER_CYCLE_STARTED)
                                  .count(),
                              "Should have 2 CYCLE_STARTED events");
                          assertEquals(
                              2,
                              dispatchedTypes.stream()
                                  .filter(t -> t == ObservationType.MANAGER_CYCLE_FINISHED)
                                  .count(),
                              "Should have 2 CYCLE_FINISHED events");
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_CONVERGED),
                              "Should have CONVERGED event");
                          assertFalse(
                              dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_STALLED),
                              "Should not have STALLED event");

                          // Verify blackboard events
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.FACT_PUBLISHED),
                              "Should have FACT_PUBLISHED events");
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.FACT_SUPERSEDED),
                              "Should have FACT_SUPERSEDED events");

                          testContext.completeNow();
                        })));
  }

  @Test
  void multiCycle_stallsWhenNoProgress(VertxTestContext testContext) {
    InMemoryBlackboard blackboard = new InMemoryBlackboard(captureDispatcher, "test-session");

    // Controller with stall threshold of 2 — stalls after 2 cycles with no new facts
    AtomicInteger execCount = new AtomicInteger(0);

    // Executor that never publishes facts (no progress)
    GraphExecutor noProgressExecutor =
        (graph, parentContext) -> {
          execCount.incrementAndGet();
          return Future.succeededFuture("Cycle report with no progress");
        };

    // Custom controller: never converges, stalls when no facts after stallThreshold
    TerminationController stallController = new DefaultTerminationController(blackboard, 10, 2);

    CyclicManagerEngine engine =
        new CyclicManagerEngine(
            noProgressExecutor,
            blackboard,
            stallController,
            captureDispatcher,
            new EngineConfig(10));

    TaskGraph graph =
        new TaskGraph(List.of(new TaskNode("n1", "Spin", "GENERAL", List.of(), null)));

    SessionContext ctx = createSessionContext("test-session");

    engine
        .run(graph, ctx)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(DecisionType.STALLED, result.finalDecision().type());
                          // Should stall at cycle 2 (stallThreshold = 2)
                          assertEquals(2, result.totalCycles());
                          assertTrue(
                              dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_STALLED));
                          testContext.completeNow();
                        })));
  }

  @Test
  void multiCycle_budgetExceededAtMaxCycles(VertxTestContext testContext) {
    InMemoryBlackboard blackboard = new InMemoryBlackboard(captureDispatcher, "test-session");
    AtomicInteger execCount = new AtomicInteger(0);

    // Executor publishes new facts each cycle (so no stall) but never converges
    GraphExecutor alwaysProgressExecutor =
        (graph, parentContext) -> {
          int cycle = execCount.incrementAndGet();
          return blackboard
              .publish("n1", "Finding " + cycle, null, cycle)
              .map(f -> "Cycle " + cycle + " report");
        };

    // maxCycles=3, stallThreshold=10 (effectively no stall)
    DefaultTerminationController controller = new DefaultTerminationController(blackboard, 3, 10);

    CyclicManagerEngine engine =
        new CyclicManagerEngine(
            alwaysProgressExecutor, blackboard, controller, captureDispatcher, new EngineConfig(3));

    TaskGraph graph =
        new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));

    SessionContext ctx = createSessionContext("test-session");

    engine
        .run(graph, ctx)
        .onComplete(
            testContext.succeeding(
                result ->
                    testContext.verify(
                        () -> {
                          assertEquals(DecisionType.BUDGET_EXCEEDED, result.finalDecision().type());
                          assertEquals(3, result.totalCycles());
                          assertEquals(3, result.cycleReports().size());
                          testContext.completeNow();
                        })));
  }
}
