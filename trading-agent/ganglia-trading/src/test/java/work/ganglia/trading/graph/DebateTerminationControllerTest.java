package work.ganglia.trading.graph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.subagent.TerminationController.CycleResult;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;

class DebateTerminationControllerTest extends BaseGangliaTest {

  private InMemoryBlackboard blackboard;
  private DebateTerminationController controller;

  private static final int MAX_ROUNDS = 3;
  private static final int STALL_THRESHOLD = 3;

  @BeforeEach
  void setUp() {
    blackboard = new InMemoryBlackboard();
    controller = new DebateTerminationController(blackboard, MAX_ROUNDS, STALL_THRESHOLD);
  }

  @Nested
  class Convergence {

    @Test
    void converges_whenBullAndBearAgreeOnDirection(VertxTestContext testContext) {
      blackboard
          .publish(
              "bull-manager",
              "AAPL is bullish",
              null,
              1,
              Map.of("role", "bull", "stance", "BULLISH"))
          .compose(
              v ->
                  blackboard.publish(
                      "bear-manager",
                      "AAPL is bullish too",
                      null,
                      1,
                      Map.of("role", "bear", "stance", "BULLISH")))
          .compose(v -> controller.evaluate(1, new CycleResult(false, "test")))
          .onComplete(
              testContext.succeeding(
                  decision ->
                      testContext.verify(
                          () -> {
                            assertEquals(DecisionType.CONVERGED, decision.type());
                            assertNotNull(decision.reason());
                            testContext.completeNow();
                          })));
    }

    @Test
    void converges_whenValidationPassedRegardless(VertxTestContext testContext) {
      assertFutureSuccess(
          controller.evaluate(1, new CycleResult(true, "all passed")),
          testContext,
          decision -> {
            assertEquals(DecisionType.CONVERGED, decision.type());
            assertNotNull(decision.reason());
          });
    }
  }

  @Nested
  class Continue {

    @Test
    void continues_whenBullAndBearDisagree(VertxTestContext testContext) {
      blackboard
          .publish(
              "bull-manager",
              "AAPL is bullish",
              null,
              1,
              Map.of("role", "bull", "stance", "BULLISH"))
          .compose(
              v ->
                  blackboard.publish(
                      "bear-manager",
                      "AAPL is bearish",
                      null,
                      1,
                      Map.of("role", "bear", "stance", "BEARISH")))
          .compose(v -> controller.evaluate(1, new CycleResult(false, "disagreement")))
          .onComplete(
              testContext.succeeding(
                  decision ->
                      testContext.verify(
                          () -> {
                            assertEquals(DecisionType.CONTINUE, decision.type());
                            assertNotNull(decision.reason());
                            testContext.completeNow();
                          })));
    }
  }

  @Nested
  class BudgetExceeded {

    @Test
    void budgetExceeded_whenMaxDebateRoundsReached(VertxTestContext testContext) {
      blackboard
          .publish(
              "bull-manager",
              "AAPL is bullish",
              null,
              3,
              Map.of("role", "bull", "stance", "BULLISH"))
          .compose(
              v ->
                  blackboard.publish(
                      "bear-manager",
                      "AAPL is bearish",
                      null,
                      3,
                      Map.of("role", "bear", "stance", "BEARISH")))
          .compose(v -> controller.evaluate(MAX_ROUNDS, new CycleResult(false, "still disagree")))
          .onComplete(
              testContext.succeeding(
                  decision ->
                      testContext.verify(
                          () -> {
                            assertEquals(DecisionType.BUDGET_EXCEEDED, decision.type());
                            assertNotNull(decision.reason());
                            testContext.completeNow();
                          })));
    }
  }

  @Nested
  class StallDetection {

    @Test
    void stalls_whenNoNewFactsAndPastThreshold(VertxTestContext testContext) {
      // No facts published — empty blackboard
      assertFutureSuccess(
          controller.evaluate(4, new CycleResult(false, "no progress")),
          testContext,
          decision -> {
            assertEquals(DecisionType.STALLED, decision.type());
            assertNotNull(decision.reason());
          });
    }
  }
}
