package work.ganglia.kernel.hook.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
import work.ganglia.util.VertxProcess;

@ExtendWith(VertxExtension.class)
class PreflightInterceptorTest extends BaseGangliaTest {

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
  }

  private CommandExecutor stubExecutor(String... commandResults) {
    AtomicReference<Integer> callCount = new AtomicReference<>(0);
    return (command, workingDir, context) -> {
      int idx = callCount.getAndUpdate(v -> v + 1);
      if (idx < commandResults.length) {
        return Future.succeededFuture(new VertxProcess.Result(0, commandResults[idx]));
      }
      return Future.succeededFuture(new VertxProcess.Result(0, ""));
    };
  }

  @Nested
  class CleanEnvironment {

    @Test
    void passesThrough_whenEnvironmentClean(VertxTestContext ctx) {
      // git status --porcelain returns empty when clean
      var executor = stubExecutor("");

      var interceptor = new PreflightInterceptor(executor, List.of("git status --porcelain"));
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          interceptor.preTurn(session, "do something"),
          ctx,
          result -> assertEquals(session.sessionId(), result.sessionId()));
    }
  }

  @Nested
  class DirtyEnvironment {

    @Test
    void fails_whenGitStatusShowsUncommittedChanges(VertxTestContext ctx) {
      // git status returns dirty
      CommandExecutor executor =
          (command, workingDir, context) -> {
            if (command.contains("git status")) {
              return Future.succeededFuture(
                  new VertxProcess.Result(0, "M  src/main/java/Foo.java\n?? untracked.txt"));
            }
            return Future.succeededFuture(new VertxProcess.Result(0, ""));
          };

      var interceptor = new PreflightInterceptor(executor, List.of("git status --porcelain"));
      var session = createSessionContext("test-session");

      assertFutureFailure(
          interceptor.preTurn(session, "do something"),
          ctx,
          error -> {
            assertTrue(error.getMessage().contains("Preflight check failed"));
            assertTrue(error.getMessage().contains("git status"));
          });
    }
  }

  @Nested
  class CommandFailure {

    @Test
    void fails_whenPreflightCommandFails(VertxTestContext ctx) {
      CommandExecutor executor =
          (command, workingDir, context) ->
              Future.succeededFuture(new VertxProcess.Result(1, "fatal: not a git repository"));

      var interceptor = new PreflightInterceptor(executor, List.of("git status --porcelain"));
      var session = createSessionContext("test-session");

      assertFutureFailure(
          interceptor.preTurn(session, "do something"),
          ctx,
          error -> assertTrue(error.getMessage().contains("Preflight check failed")));
    }
  }

  @Nested
  class NoChecks {

    @Test
    void passesThrough_whenNoChecksConfigured(VertxTestContext ctx) {
      var interceptor = new PreflightInterceptor(stubExecutor(), List.of());
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          interceptor.preTurn(session, "do something"),
          ctx,
          result -> assertEquals(session.sessionId(), result.sessionId()));
    }
  }
}
