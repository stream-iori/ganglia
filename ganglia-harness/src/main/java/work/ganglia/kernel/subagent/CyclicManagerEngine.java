package work.ganglia.kernel.subagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.subagent.TerminationController.CycleResult;
import work.ganglia.kernel.subagent.TerminationController.Decision;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;
import work.ganglia.kernel.subagent.blackboard.BlackboardSummarizer;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ObservationType;
import work.ganglia.port.internal.state.Blackboard;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.port.internal.worktree.WorktreeManager;

/**
 * Outer-loop orchestrator that drives iterative Manager collaboration. Wraps {@link GraphExecutor}
 * to execute the same graph in cycles until the {@link TerminationController} decides to stop.
 *
 * <p>Execution model per cycle:
 *
 * <ol>
 *   <li>Dispatch MANAGER_CYCLE_STARTED
 *   <li>Execute the TaskGraph via GraphExecutor
 *   <li>If worktree nodes exist: run MergeGate to merge all worktree branches
 *   <li>Evaluate termination condition (with merge results)
 *   <li>Dispatch MANAGER_CYCLE_FINISHED
 *   <li>If terminal: dispatch CONVERGED/STALLED, return result
 *   <li>If continue: inject failure context, repeat
 * </ol>
 */
public class CyclicManagerEngine {
  private static final Logger logger = LoggerFactory.getLogger(CyclicManagerEngine.class);

  private final GraphExecutor graphExecutor;
  private final Blackboard blackboard;
  private final TerminationController terminationController;
  private final ObservationDispatcher dispatcher;
  private final EngineConfig config;
  private final WorktreeConfig worktreeConfig;
  private final CycleAwareTrigger cycleAwareTrigger;
  private final BlackboardSummarizer summarizer;
  private final AgentLoopFactory loopFactory;

  /** Configuration for the cyclic engine. */
  public record EngineConfig(int maxCycles) {
    public EngineConfig {
      if (maxCycles < 1) {
        throw new IllegalArgumentException("maxCycles must be >= 1, got " + maxCycles);
      }
    }
  }

  /** Groups worktree-related configuration for merge gate operations. */
  public record WorktreeConfig(
      WorktreeManager worktreeManager, MergeGate.Validator mergeValidator, String targetBranch) {}

  /** Context passed to dynamic graph builders for per-cycle graph construction. */
  public record CycleContext(
      int cycleNumber, Blackboard blackboard, List<String> previousCycleReports) {}

  /** The final result of the engine's execution across all cycles. */
  public record EngineResult(
      Decision finalDecision,
      int totalCycles,
      String aggregatedReport,
      List<String> cycleReports) {}

  /**
   * Creates a CyclicManagerEngine without summarization support.
   *
   * @param graphExecutor executor for task graphs
   * @param blackboard shared fact store
   * @param terminationController termination decision logic
   * @param dispatcher observation dispatcher
   * @param config engine configuration
   * @param cycleAwareTrigger trigger for summarization (if null, summarization is disabled)
   * @param summarizer summarizer for archiving old facts (if null, summarization is disabled)
   * @param loopFactory factory for creating agent loops (needed for summarization)
   */
  public CyclicManagerEngine(
      GraphExecutor graphExecutor,
      Blackboard blackboard,
      TerminationController terminationController,
      ObservationDispatcher dispatcher,
      EngineConfig config,
      CycleAwareTrigger cycleAwareTrigger,
      BlackboardSummarizer summarizer,
      work.ganglia.kernel.loop.AgentLoopFactory loopFactory) {
    this(
        graphExecutor,
        blackboard,
        terminationController,
        dispatcher,
        config,
        null,
        cycleAwareTrigger,
        summarizer,
        loopFactory);
  }

  /** Legacy constructor without worktree support and without summarization. */
  public CyclicManagerEngine(
      GraphExecutor graphExecutor,
      Blackboard blackboard,
      TerminationController terminationController,
      ObservationDispatcher dispatcher,
      EngineConfig config) {
    this(
        graphExecutor,
        blackboard,
        terminationController,
        dispatcher,
        config,
        null,
        null,
        null,
        null);
  }

  /** Full constructor with worktree and merge gate support. */
  public CyclicManagerEngine(
      GraphExecutor graphExecutor,
      Blackboard blackboard,
      TerminationController terminationController,
      ObservationDispatcher dispatcher,
      EngineConfig config,
      WorktreeConfig worktreeConfig) {
    this(
        graphExecutor,
        blackboard,
        terminationController,
        dispatcher,
        config,
        worktreeConfig,
        null,
        null,
        null);
  }

  /**
   * Full constructor with worktree, merge gate, and summarization support.
   *
   * @param graphExecutor executor for task graphs
   * @param blackboard shared fact store
   * @param terminationController termination decision logic
   * @param dispatcher observation dispatcher
   * @param config engine configuration
   * @param worktreeConfig worktree configuration for merge gate (nullable)
   * @param cycleAwareTrigger trigger for summarization (if null, summarization is disabled)
   * @param summarizer summarizer for archiving old facts (if null, summarization is disabled)
   * @param loopFactory factory for creating agent loops (needed for summarization)
   */
  public CyclicManagerEngine(
      GraphExecutor graphExecutor,
      Blackboard blackboard,
      TerminationController terminationController,
      ObservationDispatcher dispatcher,
      EngineConfig config,
      WorktreeConfig worktreeConfig,
      CycleAwareTrigger cycleAwareTrigger,
      BlackboardSummarizer summarizer,
      work.ganglia.kernel.loop.AgentLoopFactory loopFactory) {
    this.graphExecutor = graphExecutor;
    this.blackboard = blackboard;
    this.terminationController = terminationController;
    this.dispatcher = dispatcher;
    this.config = config;
    this.worktreeConfig = worktreeConfig;
    this.cycleAwareTrigger = cycleAwareTrigger;
    this.summarizer = summarizer;
    this.loopFactory = loopFactory;
  }

  /**
   * Runs the manager graph in iterative cycles until convergence, budget exhaustion, or stall.
   *
   * @param graph the task graph to execute each cycle
   * @param parentContext the parent session context
   * @return the aggregated result across all cycles
   */
  public Future<EngineResult> run(TaskGraph graph, SessionContext parentContext) {
    return run(ctx -> graph, parentContext);
  }

  /**
   * Runs with a dynamic graph builder that reconstructs the TaskGraph each cycle. Useful for debate
   * patterns where per-cycle prompts must incorporate previous-cycle results.
   *
   * @param graphBuilder function that builds a TaskGraph given the current cycle context
   * @param parentContext the parent session context
   * @return the aggregated result across all cycles
   */
  public Future<EngineResult> run(
      Function<CycleContext, TaskGraph> graphBuilder, SessionContext parentContext) {
    logger.info(
        "Starting CyclicManagerEngine for session {} with maxCycles={}",
        parentContext.sessionId(),
        config.maxCycles());

    List<String> cycleReports = new ArrayList<>();

    // Clean up orphan worktrees from previous crashes
    Future<Void> startupFuture =
        worktreeConfig != null
            ? worktreeConfig.worktreeManager().cleanupOrphans()
            : Future.succeededFuture();

    return startupFuture.compose(v -> executeCycle(graphBuilder, parentContext, 1, cycleReports));
  }

  private Future<EngineResult> executeCycle(
      Function<CycleContext, TaskGraph> graphBuilder,
      SessionContext parentContext,
      int cycleNumber,
      List<String> cycleReports) {

    String sessionId = parentContext.sessionId();

    // Check if summarization is needed before starting the cycle
    return checkAndRunSummarizationIfNeeded(cycleNumber, parentContext)
        .compose(
            summarizationResult -> {
              if (summarizationResult != null) {
                logger.info("Summarization completed: {}", summarizationResult);
              }

              // Dispatch cycle started
              dispatcher.dispatch(
                  sessionId,
                  ObservationType.MANAGER_CYCLE_STARTED,
                  "Cycle " + cycleNumber + " started",
                  Map.of("cycleNumber", cycleNumber, "maxCycles", config.maxCycles()));

              logger.info("Cycle {} starting for session {}", cycleNumber, sessionId);

              // Build graph for this cycle using current context
              CycleContext cycleContext =
                  new CycleContext(
                      cycleNumber,
                      blackboard,
                      Collections.unmodifiableList(new ArrayList<>(cycleReports)));
              TaskGraph graph = graphBuilder.apply(cycleContext);

              return graphExecutor.execute(graph, parentContext);
            })
        .compose(
            report -> {
              cycleReports.add(report);

              // If worktree-enabled executor, run MergeGate on collected handles
              return runMergeGateIfNeeded()
                  .compose(
                      mergeResult -> {
                        boolean validationPassed =
                            mergeResult != null ? mergeResult.allMerged() : false;
                        String validationSummary =
                            mergeResult != null ? formatMergeResult(mergeResult) : report;

                        var cycleResult = new CycleResult(validationPassed, validationSummary);

                        return terminationController
                            .evaluate(cycleNumber, cycleResult)
                            .compose(
                                decision -> {
                                  dispatcher.dispatch(
                                      sessionId,
                                      ObservationType.MANAGER_CYCLE_FINISHED,
                                      "Cycle " + cycleNumber + " finished: " + decision.type(),
                                      Map.of(
                                          "cycleNumber",
                                          cycleNumber,
                                          "decisionType",
                                          decision.type().name()));

                                  return handleDecision(
                                      decision,
                                      graphBuilder,
                                      parentContext,
                                      cycleNumber,
                                      cycleReports);
                                });
                      });
            });
  }

  private Future<MergeGate.MergeGateResult> runMergeGateIfNeeded() {
    if (worktreeConfig == null
        || worktreeConfig.mergeValidator() == null
        || worktreeConfig.targetBranch() == null) {
      return Future.succeededFuture(null);
    }

    if (!(graphExecutor instanceof DefaultGraphExecutor defaultExecutor)) {
      return Future.succeededFuture(null);
    }

    var handles = defaultExecutor.getWorktreeHandles();
    if (handles.isEmpty()) {
      return Future.succeededFuture(null);
    }

    logger.info("Running MergeGate for {} worktree handles", handles.size());
    var mergeGate =
        new MergeGate(
            worktreeConfig.worktreeManager(),
            worktreeConfig.mergeValidator(),
            worktreeConfig.targetBranch());
    return mergeGate.mergeAll(handles);
  }

  private String formatMergeResult(MergeGate.MergeGateResult result) {
    if (result.allMerged()) {
      return "All " + result.mergedCount() + " branches merged successfully";
    }
    return "Merge issues: " + String.join("; ", result.failedMerges());
  }

  private Future<String> checkAndRunSummarizationIfNeeded(
      int cycleNumber, SessionContext parentContext) {
    if (cycleAwareTrigger == null || summarizer == null) {
      return Future.succeededFuture(null);
    }

    return cycleAwareTrigger
        .shouldSummarize(cycleNumber)
        .compose(
            shouldSummarize -> {
              if (shouldSummarize) {
                logger.info(
                    "Summarization triggered at cycle {} (cycleThreshold or supersededThreshold exceeded)",
                    cycleNumber);
                return summarizer.summarize(parentContext).map(result -> result.summaryMessage());
              }
              return Future.succeededFuture(null);
            });
  }

  private Future<EngineResult> handleDecision(
      Decision decision,
      Function<CycleContext, TaskGraph> graphBuilder,
      SessionContext parentContext,
      int cycleNumber,
      List<String> cycleReports) {

    String sessionId = parentContext.sessionId();

    return switch (decision.type()) {
      case CONVERGED -> {
        dispatcher.dispatch(
            sessionId,
            ObservationType.MANAGER_GRAPH_CONVERGED,
            decision.reason(),
            Map.of("totalCycles", cycleNumber));
        logger.info("Engine converged after {} cycles", cycleNumber);
        yield Future.succeededFuture(buildResult(decision, cycleNumber, cycleReports));
      }

      case BUDGET_EXCEEDED -> {
        logger.warn("Engine budget exceeded at cycle {}", cycleNumber);
        yield Future.succeededFuture(buildResult(decision, cycleNumber, cycleReports));
      }

      case STALLED -> {
        dispatcher.dispatch(
            sessionId,
            ObservationType.MANAGER_GRAPH_STALLED,
            decision.reason(),
            Map.of("totalCycles", cycleNumber));
        logger.warn("Engine stalled at cycle {}", cycleNumber);
        yield Future.succeededFuture(buildResult(decision, cycleNumber, cycleReports));
      }

      case CONTINUE -> {
        // Hard safety limit
        if (cycleNumber >= config.maxCycles()) {
          logger.warn(
              "Engine hard limit reached at cycle {} (maxCycles={})",
              cycleNumber,
              config.maxCycles());
          var budgetDecision =
              new Decision(
                  DecisionType.BUDGET_EXCEEDED, "Hard cycle limit reached: " + config.maxCycles());
          yield Future.succeededFuture(buildResult(budgetDecision, cycleNumber, cycleReports));
        }

        logger.info("Cycle {} continuing to next cycle", cycleNumber);
        yield executeCycle(graphBuilder, parentContext, cycleNumber + 1, cycleReports);
      }
    };
  }

  private EngineResult buildResult(Decision decision, int totalCycles, List<String> cycleReports) {
    StringBuilder aggregated = new StringBuilder();
    for (int i = 0; i < cycleReports.size(); i++) {
      aggregated.append("## Cycle ").append(i + 1).append("\n");
      aggregated.append(cycleReports.get(i)).append("\n\n");
    }
    return new EngineResult(decision, totalCycles, aggregated.toString(), cycleReports);
  }
}
