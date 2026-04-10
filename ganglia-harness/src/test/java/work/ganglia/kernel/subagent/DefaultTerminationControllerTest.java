package work.ganglia.kernel.subagent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.subagent.TerminationController.CycleResult;
import work.ganglia.kernel.subagent.TerminationController.DecisionType;

@ExtendWith(VertxExtension.class)
class DefaultTerminationControllerTest extends BaseGangliaTest {

  private InMemoryBlackboard blackboard;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    blackboard = new InMemoryBlackboard();
  }

  @Nested
  class Convergence {

    @Test
    void converged_whenValidationPasses(VertxTestContext ctx) {
      var controller = new DefaultTerminationController(blackboard, 5, 3);
      var cycleResult = new CycleResult(true, "All tests passed");

      assertFutureSuccess(
          controller.evaluate(1, cycleResult),
          ctx,
          decision -> {
            assertEquals(DecisionType.CONVERGED, decision.type());
            assertNotNull(decision.reason());
          });
    }
  }

  @Nested
  class BudgetExceeded {

    @Test
    void budgetExceeded_whenCycleExceedsMaxCycles(VertxTestContext ctx) {
      var controller = new DefaultTerminationController(blackboard, 3, 3);
      var cycleResult = new CycleResult(false, "Tests failed");

      assertFutureSuccess(
          controller.evaluate(3, cycleResult),
          ctx,
          decision -> {
            assertEquals(DecisionType.BUDGET_EXCEEDED, decision.type());
            assertTrue(decision.reason().contains("3"));
          });
    }

    @Test
    void budgetExceeded_whenCycleExceedsMaxCycles_evenIfValidationPasses(VertxTestContext ctx) {
      // If at maxCycles AND validation passes, convergence wins over budget
      var controller = new DefaultTerminationController(blackboard, 3, 3);
      var cycleResult = new CycleResult(true, "All passed");

      assertFutureSuccess(
          controller.evaluate(3, cycleResult),
          ctx,
          decision -> {
            // Convergence should take priority when validation passes
            assertEquals(DecisionType.CONVERGED, decision.type());
          });
    }
  }

  @Nested
  class StallDetection {

    @Test
    void stalled_whenNoNewFactsInLastNCycles(VertxTestContext ctx) {
      // stallThreshold=2, no facts published at all
      var controller = new DefaultTerminationController(blackboard, 10, 2);
      var cycleResult = new CycleResult(false, "Tests failed");

      // Cycle 3 means at least stallThreshold cycles have passed with no new facts
      assertFutureSuccess(
          controller.evaluate(3, cycleResult),
          ctx,
          decision -> {
            assertEquals(DecisionType.STALLED, decision.type());
            assertTrue(decision.reason().toLowerCase().contains("stall"));
          });
    }

    @Test
    void notStalled_whenNewFactsExist(VertxTestContext ctx) {
      var controller = new DefaultTerminationController(blackboard, 10, 2);
      var cycleResult = new CycleResult(false, "Tests failed");

      // Publish a fact in a recent cycle
      blackboard
          .publish("manager-a", "Found a bug", null, 2)
          .compose(
              fact ->
                  controller
                      .evaluate(3, cycleResult)
                      .onComplete(
                          ctx.succeeding(
                              decision ->
                                  ctx.verify(
                                      () -> {
                                        assertEquals(DecisionType.CONTINUE, decision.type());
                                        ctx.completeNow();
                                      }))));
    }
  }

  @Nested
  class Continue {

    @Test
    void continue_whenValidationFailsButProgressMade(VertxTestContext ctx) {
      var controller = new DefaultTerminationController(blackboard, 10, 3);
      var cycleResult = new CycleResult(false, "2 tests failed");

      // Publish a fact so progress is detected
      blackboard
          .publish("manager-a", "Found root cause", null, 1)
          .compose(
              fact ->
                  controller
                      .evaluate(1, cycleResult)
                      .onComplete(
                          ctx.succeeding(
                              decision ->
                                  ctx.verify(
                                      () -> {
                                        assertEquals(DecisionType.CONTINUE, decision.type());
                                        assertTrue(
                                            decision.reason().contains("failed"),
                                            "Should include failure context");
                                        ctx.completeNow();
                                      }))));
    }
  }
}
