package work.ganglia.infrastructure.internal.worktree;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
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
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.worktree.WorktreeHandle;
import work.ganglia.util.VertxProcess;

@ExtendWith(VertxExtension.class)
class GitWorktreeManagerTest extends BaseGangliaTest {

  private CopyOnWriteArrayList<ObservationType> dispatchedTypes;
  private CopyOnWriteArrayList<String> executedCommands;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    dispatchedTypes = new CopyOnWriteArrayList<>();
    executedCommands = new CopyOnWriteArrayList<>();
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

  private CommandExecutor stubExecutor(ConcurrentHashMap<String, VertxProcess.Result> responses) {
    return (command, workingDir, context) -> {
      executedCommands.add(command);
      for (var entry : responses.entrySet()) {
        if (command.contains(entry.getKey())) {
          return Future.succeededFuture(entry.getValue());
        }
      }
      return Future.succeededFuture(new VertxProcess.Result(0, ""));
    };
  }

  @Nested
  class Create {

    @Test
    void create_createsWorktreeAndDispatches(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put("git worktree add", new VertxProcess.Result(0, ""));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      assertFutureSuccess(
          manager.create("node-a"),
          ctx,
          handle -> {
            assertNotNull(handle);
            assertTrue(handle.worktreePath().toString().contains(".ganglia/worktrees/node-a"));
            assertTrue(handle.branchName().startsWith("node-a-"));
            assertNotNull(handle.scopedMapper());
            assertTrue(dispatchedTypes.contains(ObservationType.WORKTREE_CREATED));
            assertTrue(executedCommands.stream().anyMatch(c -> c.contains("git worktree add")));
          });
    }
  }

  @Nested
  class Merge {

    @Test
    void merge_success(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put("git checkout", new VertxProcess.Result(0, ""));
      responses.put("git merge", new VertxProcess.Result(0, ""));
      responses.put("git rev-parse HEAD", new VertxProcess.Result(0, "abc123\n"));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      var handle =
          new WorktreeHandle(
              Path.of("/repo/.ganglia/worktrees/node-a-uuid"), "node-a-uuid", p -> p);

      assertFutureSuccess(
          manager.merge(handle, "main"),
          ctx,
          result -> {
            assertTrue(result.success());
            assertEquals("abc123", result.mergeCommitHash());
            assertTrue(result.conflictFiles().isEmpty());
            assertTrue(dispatchedTypes.contains(ObservationType.WORKTREE_MERGE_SUCCESS));
          });
    }

    @Test
    void merge_conflict(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put("git checkout", new VertxProcess.Result(0, ""));
      responses.put(
          "git merge",
          new VertxProcess.Result(1, "CONFLICT (content): Merge conflict in src/Foo.java"));
      responses.put(
          "git diff --name-only --diff-filter=U",
          new VertxProcess.Result(0, "src/Foo.java\nsrc/Bar.java\n"));
      responses.put("git merge --abort", new VertxProcess.Result(0, ""));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      var handle =
          new WorktreeHandle(
              Path.of("/repo/.ganglia/worktrees/node-a-uuid"), "node-a-uuid", p -> p);

      assertFutureSuccess(
          manager.merge(handle, "main"),
          ctx,
          result -> {
            assertFalse(result.success());
            assertNull(result.mergeCommitHash());
            assertEquals(2, result.conflictFiles().size());
            assertTrue(result.conflictFiles().contains("src/Foo.java"));
            assertTrue(dispatchedTypes.contains(ObservationType.WORKTREE_MERGE_CONFLICT));
          });
    }
  }

  @Nested
  class Cleanup {

    @Test
    void cleanup_removesWorktreeAndBranch(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put("git worktree remove", new VertxProcess.Result(0, ""));
      responses.put("git branch -d", new VertxProcess.Result(0, ""));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      var handle =
          new WorktreeHandle(
              Path.of("/repo/.ganglia/worktrees/node-a-uuid"), "node-a-uuid", p -> p);

      assertFutureSuccess(
          manager.cleanup(handle),
          ctx,
          v -> {
            assertTrue(dispatchedTypes.contains(ObservationType.WORKTREE_CLEANUP));
            assertTrue(executedCommands.stream().anyMatch(c -> c.contains("git worktree remove")));
            assertTrue(executedCommands.stream().anyMatch(c -> c.contains("git branch -d")));
          });
    }
  }

  @Nested
  class CleanupOrphans {

    @Test
    void cleanupOrphans_removesStaleWorktrees(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put(
          "git worktree list --porcelain",
          new VertxProcess.Result(
              0,
              """
              worktree /repo
              HEAD abc123
              branch refs/heads/main

              worktree /repo/.ganglia/worktrees/old-branch
              HEAD def456
              branch refs/heads/old-branch
              """));
      responses.put("git worktree remove", new VertxProcess.Result(0, ""));
      responses.put("git branch -d", new VertxProcess.Result(0, ""));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      assertFutureSuccess(
          manager.cleanupOrphans(),
          ctx,
          v ->
              assertTrue(
                  executedCommands.stream().anyMatch(c -> c.contains("git worktree remove"))));
    }

    @Test
    void cleanupOrphans_noop_whenNoOrphans(VertxTestContext ctx) {
      var responses = new ConcurrentHashMap<String, VertxProcess.Result>();
      responses.put(
          "git worktree list --porcelain",
          new VertxProcess.Result(
              0,
              """
              worktree /repo
              HEAD abc123
              branch refs/heads/main
              """));

      var manager =
          new GitWorktreeManager(
              stubExecutor(responses), Path.of("/repo"), captureDispatcher(), "test-session");

      assertFutureSuccess(
          manager.cleanupOrphans(),
          ctx,
          v ->
              assertFalse(
                  executedCommands.stream().anyMatch(c -> c.contains("git worktree remove"))));
    }
  }
}
