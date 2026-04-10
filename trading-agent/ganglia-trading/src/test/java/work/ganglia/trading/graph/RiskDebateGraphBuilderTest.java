package work.ganglia.trading.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.subagent.CyclicManagerEngine.CycleContext;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.kernel.subagent.TaskNode;
import work.ganglia.trading.config.TradingConfig;

class RiskDebateGraphBuilderTest {

  private static final String INVESTMENT_PLAN = "Buy 100 shares of AAPL at $150";

  private RiskDebateGraphBuilder builder;

  @BeforeEach
  void setUp() {
    builder = new RiskDebateGraphBuilder(TradingConfig.defaults(), INVESTMENT_PLAN);
  }

  private CycleContext cycleContext(int cycleNumber, List<String> previousReports) {
    return new CycleContext(cycleNumber, new InMemoryBlackboard(), previousReports);
  }

  @Nested
  class GraphStructure {

    @Test
    void buildsThreeAuditorNodes() {
      TaskGraph graph = builder.apply(cycleContext(1, List.of()));

      List<TaskNode> auditors =
          graph.nodes().stream().filter(n -> n.dependencies().isEmpty()).toList();

      assertEquals(3, auditors.size());
    }

    @Test
    void buildsPmNode() {
      TaskGraph graph = builder.apply(cycleContext(1, List.of()));

      List<TaskNode> pmNodes =
          graph.nodes().stream().filter(n -> !n.dependencies().isEmpty()).toList();

      assertEquals(1, pmNodes.size());
      TaskNode pm = pmNodes.get(0);
      assertEquals(
          List.of("risk-aggressive", "risk-neutral", "risk-conservative"), pm.dependencies());
    }

    @Test
    void correctPersonas() {
      TaskGraph graph = builder.apply(cycleContext(1, List.of()));

      TaskNode aggressive = findNode(graph, "risk-aggressive");
      TaskNode neutral = findNode(graph, "risk-neutral");
      TaskNode conservative = findNode(graph, "risk-conservative");
      TaskNode pm = findNode(graph, "portfolio-manager");

      assertEquals("RISK_AGGRESSIVE", aggressive.persona());
      assertEquals("RISK_NEUTRAL", neutral.persona());
      assertEquals("RISK_CONSERVATIVE", conservative.persona());
      assertEquals("PORTFOLIO_MANAGER", pm.persona());
    }

    @Test
    void totalNodeCountIsFour() {
      TaskGraph graph = builder.apply(cycleContext(1, List.of()));

      assertEquals(4, graph.nodes().size());
    }
  }

  @Nested
  class CycleAdaptation {

    @Test
    void injectsPreviousRoundAudits() {
      List<String> previousReports =
          List.of("Previous risk audit: exposure too high on tech sector");
      TaskGraph graph = builder.apply(cycleContext(2, previousReports));

      TaskNode aggressive = findNode(graph, "risk-aggressive");
      TaskNode neutral = findNode(graph, "risk-neutral");
      TaskNode conservative = findNode(graph, "risk-conservative");

      assertTrue(
          aggressive.task().contains("Previous risk audit: exposure too high on tech sector"));
      assertTrue(neutral.task().contains("Previous risk audit: exposure too high on tech sector"));
      assertTrue(
          conservative.task().contains("Previous risk audit: exposure too high on tech sector"));
    }
  }

  @Nested
  class MissionContext {

    @Test
    void allNodesCarryInvestmentPlanAsMission() {
      TaskGraph graph = builder.apply(cycleContext(1, List.of()));

      for (TaskNode node : graph.nodes()) {
        assertEquals(
            INVESTMENT_PLAN,
            node.missionContext(),
            "Node " + node.id() + " should carry the investment plan as mission context");
      }
    }
  }

  private static TaskNode findNode(TaskGraph graph, String id) {
    return graph.nodes().stream()
        .filter(n -> n.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Node not found: " + id));
  }
}
