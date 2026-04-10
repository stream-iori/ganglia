package work.ganglia.trading.graph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;

/**
 * Builds the Perception phase graph: 4 parallel analyst nodes feeding into 1 judge node.
 *
 * <p>Analyst nodes run independently to gather market, fundamentals, news, and social data for a
 * given ticker. The judge node aggregates all analyst reports into a unified perception summary.
 */
public class PerceptionGraphBuilder {
  private static final Logger logger = LoggerFactory.getLogger(PerceptionGraphBuilder.class);

  private static final List<String> ANALYST_IDS =
      List.of("market", "fundamentals", "news", "social");

  private static final Map<String, String> ANALYST_PERSONAS =
      Map.of(
          "market", "MARKET_ANALYST",
          "fundamentals", "FUNDAMENTALS_ANALYST",
          "news", "NEWS_ANALYST",
          "social", "SOCIAL_ANALYST");

  private static final Map<String, String> ANALYST_TASK_TEMPLATES =
      Map.of(
          "market", "Analyze market data (price action, volume, technicals) for %s.",
          "fundamentals", "Analyze fundamental data (financials, valuation, earnings) for %s.",
          "news", "Analyze recent news and press releases for %s.",
          "social",
              "Analyze social sentiment for %s. Use news data but focus exclusively on extracting: "
                  + "(1) overall sentiment polarity (bullish/bearish/neutral with a 0-100 score), "
                  + "(2) discussion intensity and trending topics, "
                  + "(3) fear/greed indicators inferred from language tone, "
                  + "(4) notable shifts in retail vs institutional sentiment. "
                  + "Do NOT repeat the news summary — synthesize pure sentiment signals.");

  private static final String JUDGE_ID = "perception-judge";
  private static final String JUDGE_PERSONA = "PERCEPTION_JUDGE";

  public TaskGraph build(String ticker) {
    logger.debug("Building perception graph for ticker={}", ticker);
    List<TaskNode> nodes = new ArrayList<>();

    for (String id : ANALYST_IDS) {
      String task = ANALYST_TASK_TEMPLATES.get(id).formatted(ticker);
      String persona = ANALYST_PERSONAS.get(id);
      nodes.add(new TaskNode(id, task, persona, List.of(), Map.of()));
    }

    String judgeTask =
        "Aggregate and synthesize all analyst reports for %s into a unified perception summary."
            .formatted(ticker);
    nodes.add(new TaskNode(JUDGE_ID, judgeTask, JUDGE_PERSONA, List.copyOf(ANALYST_IDS), Map.of()));

    return new TaskGraph(nodes);
  }
}
