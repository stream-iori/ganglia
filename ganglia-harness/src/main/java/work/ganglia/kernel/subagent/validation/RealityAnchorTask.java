package work.ganglia.kernel.subagent.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskResult;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.CommandExecutor;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.util.VertxProcess;

/**
 * Non-LLM validation task that runs shell commands and collects pass/fail results. Used as the
 * cycle-end arbiter in the CyclicManagerEngine.
 *
 * <p>Key properties:
 *
 * <ul>
 *   <li>No LLM call — executes shell commands directly
 *   <li>Failed output is tagged {@code uncompressible=true} to prevent interpretation bias
 *   <li>Blocking suites cause ERROR; advisory suites are reported but don't block
 * </ul>
 */
public class RealityAnchorTask implements AgentTask {
  private static final Logger logger = LoggerFactory.getLogger(RealityAnchorTask.class);

  private final String taskId;
  private final List<ValidationSuite> suites;
  private final CommandExecutor commandExecutor;
  private final ObservationDispatcher dispatcher;

  public RealityAnchorTask(
      String taskId,
      List<ValidationSuite> suites,
      CommandExecutor commandExecutor,
      ObservationDispatcher dispatcher) {
    this.taskId = taskId;
    this.suites = suites;
    this.commandExecutor = commandExecutor;
    this.dispatcher = dispatcher;
  }

  @Override
  public String id() {
    return taskId;
  }

  @Override
  public String name() {
    return "reality_anchor";
  }

  @Override
  public Future<AgentTaskResult> execute(
      SessionContext context, ExecutionContext executionContext) {
    String sessionId = context.sessionId();
    dispatcher.dispatch(
        sessionId,
        ObservationType.REALITY_ANCHOR_STARTED,
        "Running %d validation suites".formatted(suites.size()),
        Map.of("suiteCount", suites.size()));

    logger.info(
        "RealityAnchor starting {} validation suites for session {}", suites.size(), sessionId);

    return executeSuites(sessionId, 0, new ArrayList<>());
  }

  private Future<AgentTaskResult> executeSuites(
      String sessionId, int index, List<SuiteResult> results) {
    if (index >= suites.size()) {
      return Future.succeededFuture(buildFinalResult(sessionId, results));
    }

    ValidationSuite suite = suites.get(index);
    logger.info("Running suite [{}]: {}", suite.name(), suite.command());

    return commandExecutor
        .execute(suite.command(), null, null)
        .recover(
            err -> {
              // Command execution itself failed (e.g., command not found)
              logger.error("Suite [{}] execution error: {}", suite.name(), err.getMessage());
              return Future.succeededFuture(new VertxProcess.Result(-1, err.getMessage()));
            })
        .compose(
            processResult -> {
              boolean passed = processResult.succeeded();
              results.add(new SuiteResult(suite, passed, processResult.output()));
              logger.info("Suite [{}]: {}", suite.name(), passed ? "PASSED" : "FAILED");
              return executeSuites(sessionId, index + 1, results);
            });
  }

  private AgentTaskResult buildFinalResult(String sessionId, List<SuiteResult> results) {
    boolean hasBlockingFailure =
        results.stream().anyMatch(r -> !r.passed && r.suite.blockOnFailure());

    StringBuilder output = new StringBuilder("# RealityAnchor Validation Report\n\n");
    for (SuiteResult r : results) {
      String status = r.passed ? "PASSED" : "FAILED";
      String blocking = r.suite.blockOnFailure() ? "blocking" : "advisory";
      output
          .append("## %s: %s (%s)\n".formatted(r.suite.name(), status, blocking))
          .append(r.rawOutput)
          .append("\n\n");
    }

    if (hasBlockingFailure) {
      dispatcher.dispatch(
          sessionId,
          ObservationType.REALITY_ANCHOR_FAILED,
          "Validation failed",
          Map.of("results", formatSummary(results)));
      logger.warn("RealityAnchor validation FAILED for session {}", sessionId);
      return new AgentTaskResult(
          AgentTaskResult.Status.ERROR, output.toString(), null, Map.of("uncompressible", true));
    }

    dispatcher.dispatch(
        sessionId,
        ObservationType.REALITY_ANCHOR_PASSED,
        "All validations passed",
        Map.of("results", formatSummary(results)));
    logger.info("RealityAnchor validation PASSED for session {}", sessionId);
    return AgentTaskResult.success(output.toString());
  }

  private String formatSummary(List<SuiteResult> results) {
    StringBuilder sb = new StringBuilder();
    for (SuiteResult r : results) {
      sb.append(r.suite.name()).append(": ").append(r.passed ? "PASSED" : "FAILED").append("; ");
    }
    return sb.toString();
  }

  private record SuiteResult(ValidationSuite suite, boolean passed, String rawOutput) {}
}
