package work.ganglia.trading.graph;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import work.ganglia.kernel.subagent.TaskGraph;

class TraderGraphBuilderTest {

  @Test
  void buildsSingleTraderNode() {
    var builder = new TraderGraphBuilder("Investment plan: buy AAPL");
    TaskGraph graph = builder.build();

    assertEquals(1, graph.nodes().size());
    var node = graph.nodes().get(0);
    assertEquals("trader", node.id());
    assertEquals("TRADER", node.persona());
    assertTrue(node.dependencies().isEmpty());
    assertTrue(node.task().contains("trading action"));
    assertEquals("Investment plan: buy AAPL", node.missionContext());
  }
}
