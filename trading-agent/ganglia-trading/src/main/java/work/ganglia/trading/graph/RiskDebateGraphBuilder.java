package work.ganglia.trading.graph;

import java.util.ArrayList;
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
 * Builds the Risk Debate phase graph: 3 parallel auditor nodes (Aggressive, Neutral, Conservative)
 * feeding into 1 Portfolio Manager synthesis node.
 *
 * <p>When cycleNumber > 1, previous cycle reports are injected into auditor task descriptions so
 * each auditor can refine its assessment based on prior round feedback.
 */
public class RiskDebateGraphBuilder implements Function<CycleContext, TaskGraph> {
  private static final Logger logger = LoggerFactory.getLogger(RiskDebateGraphBuilder.class);

  private static final String AGGRESSIVE_ID = "risk-aggressive";
  private static final String NEUTRAL_ID = "risk-neutral";
  private static final String CONSERVATIVE_ID = "risk-conservative";
  private static final String PM_ID = "portfolio-manager";

  private static final List<String> AUDITOR_IDS =
      List.of(AGGRESSIVE_ID, NEUTRAL_ID, CONSERVATIVE_ID);

  private static final Map<String, String> AUDITOR_PERSONAS =
      Map.of(
          AGGRESSIVE_ID, "RISK_AGGRESSIVE",
          NEUTRAL_ID, "RISK_NEUTRAL",
          CONSERVATIVE_ID, "RISK_CONSERVATIVE");

  private static final Map<String, String> AUDITOR_TASK_TEMPLATES =
      Map.of(
          AGGRESSIVE_ID,
              "Evaluate the investment plan from an aggressive risk perspective."
                  + " Focus on upside potential and acceptable risk tolerance.",
          NEUTRAL_ID,
              "Evaluate the investment plan from a balanced risk perspective."
                  + " Weigh both upside potential and downside risk equally.",
          CONSERVATIVE_ID,
              "Evaluate the investment plan from a conservative risk perspective."
                  + " Focus on capital preservation and downside protection.");

  private final TradingConfig config;
  private final String investmentPlan;

  public RiskDebateGraphBuilder(TradingConfig config, String investmentPlan) {
    this.config = config;
    this.investmentPlan = investmentPlan;
  }

  @Override
  public TaskGraph apply(CycleContext ctx) {
    logger.debug("Building risk debate graph for cycle {}", ctx.cycleNumber());
    List<TaskNode> nodes = new ArrayList<>();

    String priorContext = CycleContextUtil.buildPriorContext(ctx);

    for (String id : AUDITOR_IDS) {
      String baseTask = AUDITOR_TASK_TEMPLATES.get(id);
      String task = priorContext.isEmpty() ? baseTask : baseTask + "\n\n" + priorContext;
      String persona = AUDITOR_PERSONAS.get(id);
      nodes.add(
          new TaskNode(
              id,
              task,
              persona,
              List.of(),
              Map.of(),
              investmentPlan,
              ExecutionMode.SELF,
              IsolationLevel.NONE));
    }

    String pmTask =
        "Synthesize all auditor risk assessments into a final portfolio risk recommendation.";
    nodes.add(
        new TaskNode(
            PM_ID,
            pmTask,
            "PORTFOLIO_MANAGER",
            List.copyOf(AUDITOR_IDS),
            Map.of(),
            investmentPlan,
            ExecutionMode.SELF,
            IsolationLevel.NONE));

    return new TaskGraph(nodes);
  }
}
