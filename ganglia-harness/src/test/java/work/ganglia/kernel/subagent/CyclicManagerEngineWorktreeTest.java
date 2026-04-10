package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
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
import work.ganglia.kernel.subagent.CyclicManagerEngine.WorktreeConfig;
import work.ganglia.kernel.subagent.TerminationController.CycleResult;
import work.ganglia.kernel.subagent.TerminationController.Decision;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.worktree.MergeResult;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;

@ExtendWith(VertxExtension.class)
class CyclicManagerEngineWorktreeTest extends BaseGangliaTest {

  private InMemoryBlackboard blackboard;
  private CopyOnWriteArrayList<ObservationType> dispatchedTypes;
  private CopyOnWriteArrayList<String> cleanedBranches;
  private CopyOnWriteArrayList<String> createdBranches;
  private boolean orphansCleaned;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    blackboard = new InMemoryBlackboard();
    dispatchedTypes = new CopyOnWriteArrayList<>();
    cleanedBranches = new CopyOnWriteArrayList<>();
    createdBranches = new CopyOnWriteArrayList<>();
    orphansCleaned = false;
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

  private WorktreeManager stubWorktreeManager(Map<String, MergeResult> mergeResults) {
    return new WorktreeManager() {
      @Override
      public Future<WorktreeHandle> create(String branchPrefix) {
        String branch = branchPrefix + "-uuid";
        createdBranches.add(branch);
        return Future.succeededFuture(
            new WorktreeHandle(Path.of("/repo/.ganglia/worktrees/" + branch), branch, p -> p));
      }

      @Override
      public Future<MergeResult> merge(WorktreeHandle handle, String targetBranch) {
        var result = mergeResults.get(handle.branchName());
        return result != null
            ? Future.succeededFuture(result)
            : Future.succeededFuture(MergeResult.success("abc123"));
      }

      @Override
      public Future<Void> cleanup(WorktreeHandle handle) {
        cleanedBranches.add(handle.branchName());
        return Future.succeededFuture();
      }

      @Override
      public Future<Void> cleanupOrphans() {
        orphansCleaned = true;
        return Future.succeededFuture();
      }

      @Override
      public Future<Void> revert(String branchName, String targetBranch) {
        return Future.succeededFuture();
      }
    };
  }

  /**
   * Creates a GraphExecutor that simulates worktree handle collection. Since CyclicManagerEngine
   * uses the GraphExecutor interface, we return a DefaultGraphExecutor with worktree support.
   */
  private DefaultGraphExecutor worktreeGraphExecutor(WorktreeManager worktreeManager) {
    return new DefaultGraphExecutor(
        () ->
            new work.ganglia.kernel.loop.AgentLoop() {
              @Override
              public Future<String> run(
                  String userInput, work.ganglia.port.chat.SessionContext context) {
                return Future.succeededFuture("Node result");
              }

              @Override
              public Future<String> run(
                  String userInput,
                  work.ganglia.port.chat.SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return run(userInput, context);
              }

              @Override
              public Future<String> resume(
                  String askId,
                  String toolOutput,
                  work.ganglia.port.chat.SessionContext context,
                  work.ganglia.port.internal.state.AgentSignal signal) {
                return Future.succeededFuture("resumed");
              }

              @Override
              public void stop(String sessionId) {}
            },
        worktreeManager);
  }

  @Nested
  class MergeGateIntegration {

    @Test
    void worktreeNodes_mergedAfterExecution(VertxTestContext ctx) {
      var worktreeManager = stubWorktreeManager(Map.of("w1-uuid", MergeResult.success("aaa111")));

      var graphExecutor = worktreeGraphExecutor(worktreeManager);

      MergeGate.Validator validator =
          () -> Future.succeededFuture(AgentTaskResult.success("Tests passed"));

      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            // Verify merge result is reflected in cycle result
            assertTrue(cycleResult.validationPassed(), "Merge should have passed");
            return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Done"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor,
              blackboard,
              controller,
              captureDispatcher(),
              new EngineConfig(3),
              new WorktreeConfig(worktreeManager, validator, "main"));

      var graph =
          new TaskGraph(
              List.of(
                  new TaskNode(
                      "w1",
                      "Refactor",
                      "REFACTORER",
                      List.of(),
                      null,
                      null,
                      ExecutionMode.SELF,
                      IsolationLevel.WORKTREE)));
      var session = createSessionContext("test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertTrue(cleanedBranches.contains("w1-uuid"));
          });
    }

    @Test
    void mergeConflict_reportedInCycleResult(VertxTestContext ctx) {
      var worktreeManager =
          stubWorktreeManager(Map.of("w1-uuid", MergeResult.conflict(List.of("src/Foo.java"))));

      var graphExecutor = worktreeGraphExecutor(worktreeManager);

      MergeGate.Validator validator =
          () -> Future.succeededFuture(AgentTaskResult.success("Tests passed"));

      CopyOnWriteArrayList<CycleResult> capturedCycleResults = new CopyOnWriteArrayList<>();

      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            capturedCycleResults.add(cycleResult);
            return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Done"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor,
              blackboard,
              controller,
              captureDispatcher(),
              new EngineConfig(3),
              new WorktreeConfig(worktreeManager, validator, "main"));

      var graph =
          new TaskGraph(
              List.of(
                  new TaskNode(
                      "w1",
                      "Refactor",
                      "REFACTORER",
                      List.of(),
                      null,
                      null,
                      ExecutionMode.SELF,
                      IsolationLevel.WORKTREE)));
      var session = createSessionContext("test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertFalse(capturedCycleResults.isEmpty());
            assertFalse(
                capturedCycleResults.get(0).validationPassed(),
                "Merge conflict should cause validation to fail");
          });
    }
  }

  @Nested
  class WorktreeLifecycle {

    @Test
    void cleanupOrphans_calledAtStartup(VertxTestContext ctx) {
      var worktreeManager = stubWorktreeManager(Map.of());

      GraphExecutor graphExecutor = (graph, parentContext) -> Future.succeededFuture("Done");

      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Done"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor,
              blackboard,
              controller,
              captureDispatcher(),
              new EngineConfig(3),
              new WorktreeConfig(worktreeManager, null, "main"));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Task", "GENERAL", List.of(), null)));
      var session = createSessionContext("test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> assertTrue(orphansCleaned, "cleanupOrphans should be called at startup"));
    }

    @Test
    void noWorktreeManager_worksWithoutMerge(VertxTestContext ctx) {
      // Legacy behavior: no WorktreeManager, no MergeGate
      GraphExecutor graphExecutor = (graph, parentContext) -> Future.succeededFuture("Done");

      TerminationController controller =
          (cycleNumber, cycleResult) ->
              Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Done"));

      var engine =
          new CyclicManagerEngine(
              graphExecutor, blackboard, controller, captureDispatcher(), new EngineConfig(3));

      var graph = new TaskGraph(List.of(new TaskNode("n1", "Task", "GENERAL", List.of(), null)));
      var session = createSessionContext("test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertFalse(orphansCleaned);
          });
    }
  }

  @Nested
  class EndToEnd {

    @Test
    void fullCycle_worktreeExecution_merge_validate_converge(VertxTestContext ctx) {
      var worktreeManager =
          stubWorktreeManager(
              Map.of(
                  "writer-a-uuid", MergeResult.success("aaa111"),
                  "writer-b-uuid", MergeResult.success("bbb222")));

      var graphExecutor = worktreeGraphExecutor(worktreeManager);

      MergeGate.Validator validator =
          () -> Future.succeededFuture(AgentTaskResult.success("All tests passed"));

      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            if (cycleResult.validationPassed()) {
              return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "All merged"));
            }
            return Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Retry"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor,
              blackboard,
              controller,
              captureDispatcher(),
              new EngineConfig(5),
              new WorktreeConfig(worktreeManager, validator, "main"));

      var graph =
          new TaskGraph(
              List.of(
                  new TaskNode(
                      "writer-a",
                      "Task A",
                      "REFACTORER",
                      List.of(),
                      null,
                      null,
                      ExecutionMode.SELF,
                      IsolationLevel.WORKTREE),
                  new TaskNode(
                      "writer-b",
                      "Task B",
                      "REFACTORER",
                      List.of(),
                      null,
                      null,
                      ExecutionMode.SELF,
                      IsolationLevel.WORKTREE)));
      var session = createSessionContext("e2e-test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertEquals(1, result.totalCycles());
            assertEquals(2, cleanedBranches.size());
            assertTrue(orphansCleaned);
          });
    }

    @Test
    void fullCycle_mergeConflict_triggersNewCycle(VertxTestContext ctx) {
      AtomicInteger cycleCount = new AtomicInteger(0);

      // First cycle: conflict. Second cycle: success (simulate fix).
      var mergeResults = new CopyOnWriteArrayList<Map<String, MergeResult>>();
      mergeResults.add(Map.of("w1-uuid", MergeResult.conflict(List.of("src/Foo.java"))));
      mergeResults.add(Map.of("w1-uuid", MergeResult.success("fixed123")));

      var worktreeManager =
          new WorktreeManager() {
            @Override
            public Future<WorktreeHandle> create(String branchPrefix) {
              String branch = branchPrefix + "-uuid";
              createdBranches.add(branch);
              return Future.succeededFuture(
                  new WorktreeHandle(
                      Path.of("/repo/.ganglia/worktrees/" + branch), branch, p -> p));
            }

            @Override
            public Future<MergeResult> merge(WorktreeHandle handle, String targetBranch) {
              int cycle = cycleCount.get();
              Map<String, MergeResult> cycleResults =
                  cycle < mergeResults.size() ? mergeResults.get(cycle) : Map.of();
              MergeResult result = cycleResults.get(handle.branchName());
              return result != null
                  ? Future.succeededFuture(result)
                  : Future.succeededFuture(MergeResult.success("default"));
            }

            @Override
            public Future<Void> cleanup(WorktreeHandle handle) {
              cleanedBranches.add(handle.branchName());
              return Future.succeededFuture();
            }

            @Override
            public Future<Void> cleanupOrphans() {
              orphansCleaned = true;
              return Future.succeededFuture();
            }

            @Override
            public Future<Void> revert(String branchName, String targetBranch) {
              return Future.succeededFuture();
            }
          };

      var graphExecutor = worktreeGraphExecutor(worktreeManager);

      MergeGate.Validator validator =
          () -> Future.succeededFuture(AgentTaskResult.success("Tests passed"));

      TerminationController controller =
          (cycleNumber, cycleResult) -> {
            cycleCount.set(cycleNumber);
            if (cycleResult.validationPassed()) {
              return Future.succeededFuture(new Decision(DecisionType.CONVERGED, "Fixed"));
            }
            return Future.succeededFuture(new Decision(DecisionType.CONTINUE, "Retry merge"));
          };

      var engine =
          new CyclicManagerEngine(
              graphExecutor,
              blackboard,
              controller,
              captureDispatcher(),
              new EngineConfig(5),
              new WorktreeConfig(worktreeManager, validator, "main"));

      var graph =
          new TaskGraph(
              List.of(
                  new TaskNode(
                      "w1",
                      "Fix bug",
                      "REFACTORER",
                      List.of(),
                      null,
                      null,
                      ExecutionMode.SELF,
                      IsolationLevel.WORKTREE)));
      var session = createSessionContext("conflict-test");

      assertFutureSuccess(
          engine.run(graph, session),
          ctx,
          result -> {
            assertEquals(DecisionType.CONVERGED, result.finalDecision().type());
            assertEquals(2, result.totalCycles());
          });
    }
  }
}
