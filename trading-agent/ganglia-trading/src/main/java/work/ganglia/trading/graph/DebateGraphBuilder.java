package work.ganglia.trading.graph;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.kernel.subagent.CyclicManagerEngine.CycleContext;
import work.ganglia.kernel.subagent.ExecutionMode;
import work.ganglia.kernel.subagent.IsolationLevel;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;
import work.ganglia.trading.config.TradingConfig;

/**
 * Builds a debate TaskGraph with Bull and Bear researchers in parallel, followed by a Judge node
 * that depends on both. On cycles after the first, previous-round arguments are injected into the
 * bull and bear task descriptions.
 */
public class DebateGraphBuilder implements Function<CycleContext, TaskGraph> {
  private static final Logger logger = LoggerFactory.getLogger(DebateGraphBuilder.class);

  private final TradingConfig config;
  private final String perceptionReport;

  public DebateGraphBuilder(TradingConfig config, String perceptionReport) {
    this.config = config;
    this.perceptionReport = perceptionReport;
  }

  @Override
  public TaskGraph apply(CycleContext ctx) {
    logger.debug("Building debate graph for cycle {}", ctx.cycleNumber());
    String bullTask = buildResearcherTask("bullish", ctx);
    String bearTask = buildResearcherTask("bearish", ctx);
    String judgeTask =
        "Evaluate the bull and bear arguments and render a final investment verdict.";

    var bull =
        new TaskNode(
            "bull",
            bullTask,
            "BULL_RESEARCHER",
            List.of(),
            Map.of(),
            perceptionReport,
            ExecutionMode.SELF,
            IsolationLevel.NONE);

    var bear =
        new TaskNode(
            "bear",
            bearTask,
            "BEAR_RESEARCHER",
            List.of(),
            Map.of(),
            perceptionReport,
            ExecutionMode.SELF,
            IsolationLevel.NONE);

    var judge =
        new TaskNode(
            "debate-judge",
            judgeTask,
            "INVEST_JUDGE",
            List.of("bull", "bear"),
            Map.of(),
            perceptionReport,
            ExecutionMode.SELF,
            IsolationLevel.NONE);

    return new TaskGraph(List.of(bull, bear, judge));
  }

  private String buildResearcherTask(String perspective, CycleContext ctx) {
    String baseTask =
        "Analyze the investment opportunity from a %s perspective.".formatted(perspective);
    String priorContext = CycleContextUtil.buildPriorContext(ctx);
    return priorContext.isEmpty() ? baseTask : baseTask + "\n\n" + priorContext;
  }
}
