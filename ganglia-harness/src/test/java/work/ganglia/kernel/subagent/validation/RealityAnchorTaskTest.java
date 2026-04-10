package work.ganglia.kernel.subagent.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
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
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.VertxProcess;

@ExtendWith(VertxExtension.class)
class RealityAnchorTaskTest extends BaseGangliaTest {

  private CopyOnWriteArrayList<ObservationType> dispatchedTypes;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    dispatchedTypes = new CopyOnWriteArrayList<>();
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

  /** Stub executor that returns pre-configured results per command. */
  private CommandExecutor stubExecutor(Map<String, VertxProcess.Result> results) {
    return (command, workingDir, context) -> {
      var result = results.get(command);
      if (result != null) {
        return Future.succeededFuture(result);
      }
      return Future.failedFuture("Unknown command: " + command);
    };
  }

  @Nested
  class AllSuitesPass {

    @Test
    void success_whenAllSuitesPass(VertxTestContext ctx) {
      var suites =
          List.of(
              new ValidationSuite("unit-tests", "mvn test", Duration.ofMinutes(5), true),
              new ValidationSuite("lint", "npm run lint", Duration.ofMinutes(2), true));

      var executor =
          stubExecutor(
              Map.of(
                  "mvn test", new VertxProcess.Result(0, "BUILD SUCCESS"),
                  "npm run lint", new VertxProcess.Result(0, "No issues found")));

      var task = new RealityAnchorTask("ra-1", suites, executor, captureDispatcher());
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          task.execute(session, null),
          ctx,
          result -> {
            assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
            assertTrue(result.output().contains("unit-tests"));
            assertTrue(result.output().contains("lint"));
            assertTrue(result.output().contains("PASSED"));
            assertTrue(dispatchedTypes.contains(ObservationType.REALITY_ANCHOR_STARTED));
            assertTrue(dispatchedTypes.contains(ObservationType.REALITY_ANCHOR_PASSED));
            assertFalse(dispatchedTypes.contains(ObservationType.REALITY_ANCHOR_FAILED));
          });
    }
  }

  @Nested
  class BlockingFailure {

    @Test
    void error_whenBlockingSuiteFails(VertxTestContext ctx) {
      var suites =
          List.of(
              new ValidationSuite("unit-tests", "mvn test", Duration.ofMinutes(5), true),
              new ValidationSuite("lint", "npm run lint", Duration.ofMinutes(2), true));

      var executor =
          stubExecutor(
              Map.of(
                  "mvn test", new VertxProcess.Result(1, "Tests failed: 2 failures"),
                  "npm run lint", new VertxProcess.Result(0, "No issues")));

      var task = new RealityAnchorTask("ra-1", suites, executor, captureDispatcher());
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          task.execute(session, null),
          ctx,
          result -> {
            assertEquals(AgentTaskResult.Status.ERROR, result.status());
            assertTrue(result.output().contains("Tests failed"));
            assertTrue(
                result.metadata() != null
                    && Boolean.TRUE.equals(result.metadata().get("uncompressible")));
            assertTrue(dispatchedTypes.contains(ObservationType.REALITY_ANCHOR_FAILED));
          });
    }
  }

  @Nested
  class AdvisoryFailure {

    @Test
    void success_whenAdvisorySuiteFails(VertxTestContext ctx) {
      var suites =
          List.of(
              new ValidationSuite("unit-tests", "mvn test", Duration.ofMinutes(5), true),
              new ValidationSuite("style-check", "checkstyle", Duration.ofMinutes(1), false));

      var executor =
          stubExecutor(
              Map.of(
                  "mvn test", new VertxProcess.Result(0, "BUILD SUCCESS"),
                  "checkstyle", new VertxProcess.Result(1, "3 style violations")));

      var task = new RealityAnchorTask("ra-1", suites, executor, captureDispatcher());
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          task.execute(session, null),
          ctx,
          result -> {
            // Advisory failure should not block — overall SUCCESS
            assertEquals(AgentTaskResult.Status.SUCCESS, result.status());
            assertTrue(result.output().contains("style-check"));
            assertTrue(result.output().contains("FAILED"));
            assertTrue(result.output().contains("advisory"));
          });
    }
  }

  @Nested
  class CommandFailure {

    @Test
    void error_whenCommandExecutionFails(VertxTestContext ctx) {
      var suites =
          List.of(new ValidationSuite("broken", "nonexistent-cmd", Duration.ofMinutes(1), true));

      // Executor returns a failure Future
      CommandExecutor executor =
          (command, workingDir, context) ->
              Future.failedFuture(new RuntimeException("Command not found"));

      var task = new RealityAnchorTask("ra-1", suites, executor, captureDispatcher());
      var session = createSessionContext("test-session");

      assertFutureSuccess(
          task.execute(session, null),
          ctx,
          result -> {
            assertEquals(AgentTaskResult.Status.ERROR, result.status());
            assertTrue(result.output().contains("Command not found"));
          });
    }
  }

  @Nested
  class TaskIdentity {

    @Test
    void hasCorrectIdAndName() {
      var suites = List.of(new ValidationSuite("test", "echo ok", Duration.ofSeconds(10), true));
      var task =
          new RealityAnchorTask(
              "ra-42",
              suites,
              (cmd, dir, ctx) -> Future.succeededFuture(new VertxProcess.Result(0, "")),
              captureDispatcher());

      assertEquals("ra-42", task.id());
      assertEquals("reality_anchor", task.name());
    }
  }
}
