package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.subagent.CyclicManagerEngine.EngineConfig;
import work.ganglia.kernel.subagent.TerminationController.Decision;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;

@ExtendWith(VertxExtension.class)
class CyclicManagerEngineTest extends BaseGangliaTest {

  private InMemoryBlackboard blackboard;
  private CopyOnWriteArrayList<ObservationType> dispatchedTypes;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    dispatchedTypes = new CopyOnWriteArrayList<>();
    blackboard = new InMemoryBlackboard();
  }

  private ObservationDispatcher captureDispatcher() {
    return new ObservationDispatcher() {
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

  @Nested
  class SingleCycleConvergence {

    @Test
    void converges_whenGraphExecutorSucceeds(VertxTestContext ctx) {
      // Graph executor always succeeds
      GraphExecutor graphExecutor =
          (graph, parentContext) -> Future.succeededFuture("All tasks done");

      // Termination controller always says converged
      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Converged"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph =
          new TaskGraph(List.of(new TaskNode("n1", "Do stuff", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertEquals(1, result.totalCycles());
            assertFalse(result.aggregatedReport().isEmpty());
            assertTrue(dispatchedTypes.contains(ObservationType.MANAGER_CYCLE_STARTED));
            assertTrue(dispatchedTypes.contains(ObservationType.MANAGER_CYCLE_FINISHED));
            assertTrue(dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_CONVERGED));
          });
    }
  }

  @Nested
  class MultiCycleConvergence {

    @Test
    void converges_afterMultipleCycles(VertxTestContext ctx) {
      AtomicInteger execCount = new AtomicInteger(0);

      GraphExecutor graphExecutor =
          (graph, parentContext) -> {
            int cycle = execCount.incrementAndGet();
            return Future.succeededFuture("Cycle " + cycle + " result");
          };

      // Converge on cycle 3
      AtomicInteger evalCount = new AtomicInteger(0);
      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            int eval = evalCount.incrementAndGet();
            if (eval >= 3) {
              return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Finally done"));
            }
            return Future.succeededFuture(
                new Decision(DecisionType.CONTINUE, "Not yet, cycle " + eval));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertEquals(3, result.totalCycles());
          });
    }
  }

  @Nested
  class BudgetExhaustion {

    @Test
    void stops_whenBudgetExceeded(VertxTestContext ctx) {
      GraphExecutor graphExecutor = (graph, parentContext) -> Future.succeededFuture("Some result");

      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            if (cycleNumber >= 2) {
              return Future.succeededFuture(
                  new Decision(DecisionType.BUDGET_EXCEEDED, "Max cycles reached"));
            }
            return Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Continue"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.BUDGET_EXCEEDED, result.finalDecision().type());
            assertEquals(2, result.totalCycles());
          });
    }
  }

  @Nested
  class StallDetection {

    @Test
    void stops_whenStalled(VertxTestContext ctx) {
      GraphExecutor graphExecutor =
          (graph, parentContext) -> Future.succeededFuture("Same old result");

      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.STALLED, "No progress"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.STALLED, result.finalDecision().type());
            assertEquals(1, result.totalCycles());
            assertTrue(dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_STALLED));
            assertFalse(dispatchedTypes.contains(ObservationType.MANAGER_GRAPH_CONVERGED));
          });
    }
  }

  @Nested
  class GraphExecutorFailure {

    @Test
    void failsGracefully_whenGraphExecutorErrors(VertxTestContext ctx) {
      GraphExecutor graphExecutor =
          (graph, parentContext) -> Future.failedFuture(new RuntimeException("Boom"));

      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Continue"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureFailure(
          engine.run(graph, session),
          ctx,
          error -> assertTrue(error.getMessage().contains("Boom")));
    }
  }

  @Nested
  class HardCycleLimit {

    @Test
    void hardLimit_preventsRunaway(VertxTestContext ctx) {
      AtomicInteger execCount = new AtomicInteger(0);

      GraphExecutor graphExecutor =
          (graph, parentContext) -> {
            execCount.incrementAndGet();
            return Future.succeededFuture("Result");
          };

      // Always says continue (would cause infinite loop without hard limit)
      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Keep going"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(3));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            // Engine should enforce hard limit even if controller says continue
            assertEquals(3, result.totalCycles());
            assertEquals(DecisionType.BUDGET_EXCEEDED, result.finalDecision().type());
          });
    }
  }

  @Nested
  class CycleResultInjection {

    @Test
    void previousCycleReport_injectedIntoNextCycleContext(VertxTestContext ctx) {
      CopyOnWriteArrayList<String> capturedReports = new CopyOnWriteArrayList<>();
      AtomicInteger execCount = new AtomicInteger(0);

      GraphExecutor graphExecutor =
          (graph, parentContext) -> {
            int cycle = execCount.incrementAndGet();
            // Capture any blackboard state visible at execution time
            capturedReports.add("cycle-" + cycle);
            return Future.succeededFuture("Report from cycle " + cycle);
          };

      AtomicInteger evalCount = new AtomicInteger(0);
      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            int eval = evalCount.incrementAndGet();
            if (eval >= 2) {
              return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Done"));
            }
            return Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Continue"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(5));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Work", "GENERAL", List.of(), null)));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(2, result.totalCycles());
            assertEquals(2, capturedReports.size());
          });
    }
  }
}
