package work.ganglia.trading.graph;

import java.util.List;
import java.util.Map;

import work.ganglia.kernel.subagent.ExecutionMode;
import work.ganglia.kernel.subagent.IsolationLevel;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;

/**
 * Builds a single-node TaskGraph for the Trader phase. The Trader receives the Research Manager's
 * investment plan and analyst reports, then proposes a specific trading action with position
 * sizing.
 */
public class TraderGraphBuilder {

  private static final String TRADER_TASK =
      "Based on the investment plan and analyst reports, propose a specific trading action. "
          + "Include position sizing rationale, entry strategy, and risk parameters. "
          + "Conclude with: FINAL TRANSACTION PROPOSAL: **BUY/HOLD/SELL**";

  private final String investmentPlan;

  public TraderGraphBuilder(String investmentPlan) {
    this.investmentPlan = investmentPlan;
  }

  public TaskGraph build() {
    var trader =
        new TaskNode(
            "trader",
            TRADER_TASK,
            "TRADER",
            List.of(),
            Map.of(),
            investmentPlan,
            ExecutionMode.SELF,
            IsolationLevel.NONE);
    return new TaskGraph(List.of(trader));
  }
}
