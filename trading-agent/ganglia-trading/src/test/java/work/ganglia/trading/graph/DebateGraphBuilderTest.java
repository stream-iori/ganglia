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

class DebateGraphBuilderTest {

  private DebateGraphBuilder builder;
  private InMemoryBlackboard blackboard;
  private static final String PERCEPTION_REPORT = "AAPL shows strong momentum with RSI at 65";

  @BeforeEach
  void setUp() {
    blackboard = new InMemoryBlackboard();
    builder = new DebateGraphBuilder(TradingConfig.defaults(), PERCEPTION_REPORT);
  }

  @Nested
  class GraphStructure {

    @Test
    void buildsBullAndBearInParallel() {
      var ctx = new CycleContext(1, blackboard, List.of());
      TaskGraph graph = builder.apply(ctx);

      TaskNode bull = findNode(graph, "bull");
      TaskNode bear = findNode(graph, "bear");

      assertNotNull(bull);
      assertNotNull(bear);
      assertTrue(bull.dependencies().isEmpty(), "bull should have no dependencies");
      assertTrue(bear.dependencies().isEmpty(), "bear should have no dependencies");
    }

    @Test
    void buildsJudgeNode() {
      var ctx = new CycleContext(1, blackboard, List.of());
      TaskGraph graph = builder.apply(ctx);

      TaskNode judge = findNode(graph, "debate-judge");

      assertNotNull(judge);
      assertEquals(2, judge.dependencies().size());
      assertTrue(judge.dependencies().contains("bull"));
      assertTrue(judge.dependencies().contains("bear"));
    }

    @Test
    void correctPersonas() {
      var ctx = new CycleContext(1, blackboard, List.of());
      TaskGraph graph = builder.apply(ctx);

      assertEquals("BULL_RESEARCHER", findNode(graph, "bull").persona());
      assertEquals("BEAR_RESEARCHER", findNode(graph, "bear").persona());
      assertEquals("INVEST_JUDGE", findNode(graph, "debate-judge").persona());
    }
  }

  @Nested
  class CycleAdaptation {

    @Test
    void injectsPreviousRoundArguments_afterCycle1() {
      var ctx = new CycleContext(2, blackboard, List.of("Bear argued X is overvalued"));
      TaskGraph graph = builder.apply(ctx);

      TaskNode bull = findNode(graph, "bull");
      TaskNode bear = findNode(graph, "bear");

      assertTrue(
          bull.task().contains("Bear argued X is overvalued"),
          "bull task should contain previous bear argument");
      assertTrue(
          bear.task().contains("Bear argued X is overvalued"),
          "bear task should contain previous round reports");
    }

    @Test
    void cycle1_noInjection() {
      var ctx = new CycleContext(1, blackboard, List.of());
      TaskGraph graph = builder.apply(ctx);

      TaskNode bull = findNode(graph, "bull");
      TaskNode bear = findNode(graph, "bear");

      assertFalse(
          bull.task().contains("Previous round"),
          "cycle 1 bull task should not reference previous rounds");
      assertFalse(
          bear.task().contains("Previous round"),
          "cycle 1 bear task should not reference previous rounds");
    }
  }

  @Nested
  class MissionContext {

    @Test
    void allNodesCarryPerceptionReportAsMission() {
      var ctx = new CycleContext(1, blackboard, List.of());
      TaskGraph graph = builder.apply(ctx);

      for (TaskNode node : graph.nodes()) {
        assertEquals(
            PERCEPTION_REPORT,
            node.missionContext(),
            "node " + node.id() + " should carry perception report as missionContext");
      }
    }
  }

  private static TaskNode findNode(TaskGraph graph, String id) {
    return graph.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
  }
}
