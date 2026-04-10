package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.internal.worktree.MergeResult;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;

@ExtendWith(VertxExtension.class)
class MergeGateTest extends BaseGangliaTest {

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
  }

  private WorktreeManager stubWorktreeManager(
      Map<String, MergeResult> mergeResults, CopyOnWriteArrayList<String> cleanedBranches) {
    return new WorktreeManager() {
      @Override
      public Future<WorktreeHandle> create(String branchPrefix) {
        return Future.succeededFuture(null);
      }

      @Override
      public Future<MergeResult> merge(WorktreeHandle handle, String targetBranch) {
        var result = mergeResults.get(handle.branchName());
        return result != null
            ? Future.succeededFuture(result)
            : Future.failedFuture("No stub for " + handle.branchName());
      }

      @Override
      public Future<Void> cleanup(WorktreeHandle handle) {
        cleanedBranches.add(handle.branchName());
        return Future.succeededFuture();
      }

      @Override
      public Future<Void> cleanupOrphans() {
        return Future.succeededFuture();
      }

      @Override
      public Future<Void> revert(String branchName, String targetBranch) {
        return Future.succeededFuture();
      }
    };
  }

  private WorktreeHandle handle(String branch) {
    return new WorktreeHandle(Path.of("/repo/.ganglia/worktrees/" + branch), branch, p -> p);
  }

  @Nested
  class AllMergesSucceed {

    @Test
    void allPass_returnsSuccess(VertxTestContext ctx) {
      var cleaned = new CopyOnWriteArrayList<String>();
      var worktreeManager =
          stubWorktreeManager(
              Map.of(
                  "branch-a", MergeResult.success("aaa111"),
                  "branch-b", MergeResult.success("bbb222")),
              cleaned);

      var validator =
          new MergeGate.Validator() {
            @Override
            public Future<AgentTaskResult> validate() {
              return Future.succeededFuture(AgentTaskResult.success("All tests passed"));
            }
          };

      var gate = new MergeGate(worktreeManager, validator, "main");

      var handles = List.of(handle("branch-a"), handle("branch-b"));

      assertFutureSuccess(
          gate.mergeAll(handles),
          ctx,
          result -> {
            assertTrue(result.allMerged());
            assertEquals(2, result.mergedCount());
            assertEquals(0, result.failedMerges().size());
            assertEquals(2, cleaned.size());
          });
    }
  }

  @Nested
  class MergeConflict {

    @Test
    void conflict_reportsFailure(VertxTestContext ctx) {
      var cleaned = new CopyOnWriteArrayList<String>();
      var worktreeManager =
          stubWorktreeManager(
              Map.of(
                  "branch-a", MergeResult.success("aaa111"),
                  "branch-b", MergeResult.conflict(List.of("src/Foo.java"))),
              cleaned);

      var validator =
          new MergeGate.Validator() {
            @Override
            public Future<AgentTaskResult> validate() {
              return Future.succeededFuture(AgentTaskResult.success("Tests passed"));
            }
          };

      var gate = new MergeGate(worktreeManager, validator, "main");

      var handles = List.of(handle("branch-a"), handle("branch-b"));

      assertFutureSuccess(
          gate.mergeAll(handles),
          ctx,
          result -> {
            assertFalse(result.allMerged());
            assertEquals(1, result.mergedCount());
            assertEquals(1, result.failedMerges().size());
            assertTrue(result.failedMerges().get(0).contains("branch-b"));
          });
    }
  }

  @Nested
  class ValidationFailure {

    @Test
    void validationFails_reportsInResult(VertxTestContext ctx) {
      var cleaned = new CopyOnWriteArrayList<String>();
      var worktreeManager =
          stubWorktreeManager(Map.of("branch-a", MergeResult.success("aaa111")), cleaned);

      var validator =
          new MergeGate.Validator() {
            @Override
            public Future<AgentTaskResult> validate() {
              return Future.succeededFuture(AgentTaskResult.error("2 tests failed"));
            }
          };

      var gate = new MergeGate(worktreeManager, validator, "main");

      var handles = List.of(handle("branch-a"));

      assertFutureSuccess(
          gate.mergeAll(handles),
          ctx,
          result -> {
            assertFalse(result.allMerged());
            assertEquals(1, result.failedMerges().size());
            assertTrue(result.failedMerges().get(0).contains("validation failed"));
          });
    }
  }
}
