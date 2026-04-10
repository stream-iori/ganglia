package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.worktree.MergeResult;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.port.internal.worktree.WorktreeManager;

@ExtendWith(VertxExtension.class)
class DefaultGraphExecutorWorktreeTest extends BaseGangliaTest {

  private ConcurrentHashMap<String, String> capturedPrompts;
  private ConcurrentHashMap<String, SessionContext> capturedContexts;
  private CopyOnWriteArrayList<String> createdWorktrees;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    capturedPrompts = new ConcurrentHashMap<>();
    capturedContexts = new ConcurrentHashMap<>();
    createdWorktrees = new CopyOnWriteArrayList<>();
  }

  private AgentLoopFactory stubLoopFactory() {
    return () ->
        new AgentLoop() {
          @Override
          public Future<String> run(String userInput, SessionContext context) {
            capturedPrompts.put(context.sessionId(), userInput);
            capturedContexts.put(context.sessionId(), context);
            return Future.succeededFuture("Result from " + context.sessionId());
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
  }

  private WorktreeManager stubWorktreeManager() {
    return new WorktreeManager() {
      @Override
      public Future<WorktreeHandle> create(String branchPrefix) {
        String branch = branchPrefix + "-test-uuid";
        Path worktreePath = Path.of("/repo/.ganglia/worktrees/" + branch);
        createdWorktrees.add(branch);
        return Future.succeededFuture(new WorktreeHandle(worktreePath, branch, p -> p));
      }

      @Override
      public Future<MergeResult> merge(WorktreeHandle handle, String targetBranch) {
        return Future.succeededFuture(MergeResult.success("abc123"));
      }

      @Override
      public Future<Void> cleanup(WorktreeHandle handle) {
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

  @Nested
  class WorktreeIsolation {

    @Test
    void worktreeNode_createsWorktreeAndScopesContext(VertxTestContext ctx) {
      TaskNode node =
          new TaskNode(
              "writer-1",
              "Refactor module",
              "REFACTORER",
              List.of(),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.WORKTREE);

      var executor = new DefaultGraphExecutor(stubLoopFactory(), stubWorktreeManager());
      var graph = new TaskGraph(List.of(node));
      var sessionCtx = createSessionContext("parent");

      assertFutureSuccess(
          executor.execute(graph, sessionCtx),
          ctx,
          report -> {
            // Worktree was created
            assertEquals(1, createdWorktrees.size());
            assertTrue(createdWorktrees.get(0).startsWith("writer-1"));
            // Node was executed
            assertNotNull(capturedPrompts.get("parent-node-writer-1"));
            // Worktree handle is recorded in metadata
            var nodeCtx = capturedContexts.get("parent-node-writer-1");
            assertNotNull(nodeCtx);
            assertTrue(nodeCtx.metadata().containsKey("worktree_branch"));
          });
    }

    @Test
    void nonWorktreeNode_doesNotCreateWorktree(VertxTestContext ctx) {
      TaskNode node =
          new TaskNode(
              "reader-1",
              "Investigate code",
              "INVESTIGATOR",
              List.of(),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.SESSION);

      var executor = new DefaultGraphExecutor(stubLoopFactory(), stubWorktreeManager());
      var graph = new TaskGraph(List.of(node));
      var sessionCtx = createSessionContext("parent");

      assertFutureSuccess(
          executor.execute(graph, sessionCtx),
          ctx,
          report -> {
            assertEquals(0, createdWorktrees.size());
            assertNotNull(capturedPrompts.get("parent-node-reader-1"));
          });
    }

    @Test
    void mixedIsolation_onlyWorktreeNodesGetWorktrees(VertxTestContext ctx) {
      TaskNode reader = new TaskNode("reader", "Read code", "INVESTIGATOR", List.of(), null);
      TaskNode writer =
          new TaskNode(
              "writer",
              "Write code",
              "REFACTORER",
              List.of("reader"),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.WORKTREE);

      var executor = new DefaultGraphExecutor(stubLoopFactory(), stubWorktreeManager());
      var graph = new TaskGraph(List.of(reader, writer));
      var sessionCtx = createSessionContext("parent");

      assertFutureSuccess(
          executor.execute(graph, sessionCtx),
          ctx,
          report -> {
            assertEquals(1, createdWorktrees.size());
            assertTrue(createdWorktrees.get(0).startsWith("writer"));
          });
    }

    @Test
    void worktreeHandles_collectedForMerge(VertxTestContext ctx) {
      TaskNode w1 =
          new TaskNode(
              "w1",
              "Task A",
              "REFACTORER",
              List.of(),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.WORKTREE);
      TaskNode w2 =
          new TaskNode(
              "w2",
              "Task B",
              "REFACTORER",
              List.of(),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.WORKTREE);

      var executor = new DefaultGraphExecutor(stubLoopFactory(), stubWorktreeManager());
      var graph = new TaskGraph(List.of(w1, w2));
      var sessionCtx = createSessionContext("parent");

      assertFutureSuccess(
          executor.execute(graph, sessionCtx),
          ctx,
          report -> {
            assertEquals(2, createdWorktrees.size());
            assertEquals(2, executor.getWorktreeHandles().size());
          });
    }
  }

  @Nested
  class NoWorktreeManager {

    @Test
    void worktreeNode_withoutManager_fallsBackToSession(VertxTestContext ctx) {
      TaskNode node =
          new TaskNode(
              "writer-1",
              "Refactor module",
              "REFACTORER",
              List.of(),
              null,
              null,
              ExecutionMode.SELF,
              IsolationLevel.WORKTREE);

      // No WorktreeManager provided (legacy constructor)
      var executor = new DefaultGraphExecutor(stubLoopFactory());
      var graph = new TaskGraph(List.of(node));
      var sessionCtx = createSessionContext("parent");

      assertFutureSuccess(
          executor.execute(graph, sessionCtx),
          ctx,
          report -> {
            // Should still execute, just without worktree isolation
            assertNotNull(capturedPrompts.get("parent-node-writer-1"));
            assertTrue(executor.getWorktreeHandles().isEmpty());
          });
    }
  }
}
