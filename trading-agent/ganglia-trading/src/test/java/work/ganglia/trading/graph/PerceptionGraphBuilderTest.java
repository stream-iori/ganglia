package work.ganglia.trading.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;

class PerceptionGraphBuilderTest {

  private static final List<String> ANALYST_IDS =
      List.of("market", "fundamentals", "news", "social");

  private static final Map<String, String> ANALYST_PERSONAS =
      Map.of(
          "market", "MARKET_ANALYST",
          "fundamentals", "FUNDAMENTALS_ANALYST",
          "news", "NEWS_ANALYST",
          "social", "SOCIAL_ANALYST");

  private TaskGraph graph;

  @BeforeEach
  void setUp() {
    var builder = new PerceptionGraphBuilder();
    graph = builder.build("AAPL");
  }

  @Nested
  class GraphStructure {

    @Test
    void buildsFourParallelAnalystNodes() {
      List<TaskNode> analysts =
          graph.nodes().stream().filter(n -> ANALYST_IDS.contains(n.id())).toList();

      assertEquals(4, analysts.size());
      for (TaskNode analyst : analysts) {
        assertTrue(analyst.dependencies().isEmpty(), analyst.id() + " should have no dependencies");
      }
    }

    @Test
    void buildsJudgeNodeDependingOnAll() {
      TaskNode judge =
          graph.nodes().stream()
              .filter(n -> "perception-judge".equals(n.id()))
              .findFirst()
              .orElseThrow(() -> new AssertionError("perception-judge node not found"));

      assertEquals(4, judge.dependencies().size());
      assertTrue(judge.dependencies().containsAll(ANALYST_IDS));
    }

    @Test
    void totalNodeCountIsFive() {
      assertEquals(5, graph.nodes().size());
    }

    @Test
    void analystNodesHaveCorrectPersonas() {
      Map<String, String> actualPersonas =
          graph.nodes().stream()
              .filter(n -> ANALYST_IDS.contains(n.id()))
              .collect(Collectors.toMap(TaskNode::id, TaskNode::persona));

      assertEquals(ANALYST_PERSONAS, actualPersonas);
    }

    @Test
    void analystNodesHaveNoDependencies() {
      graph.nodes().stream()
          .filter(n -> ANALYST_IDS.contains(n.id()))
          .forEach(
              node ->
                  assertTrue(
                      node.dependencies().isEmpty(),
                      node.id() + " should have empty dependencies"));
    }
  }
}
