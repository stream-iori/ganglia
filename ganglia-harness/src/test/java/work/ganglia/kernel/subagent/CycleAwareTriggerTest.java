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

@ExtendWith(VertxExtension.class)
class CycleAwareTriggerTest extends BaseGangliaTest {

  private InMemoryBlackboard blackboard;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    blackboard = new InMemoryBlackboard();
  }

  @Nested
  class SupersededCountTrigger {

    @Test
    void triggers_whenSupersededCountExceedsThreshold(VertxTestContext ctx) {
      var trigger = new CycleAwareTrigger(blackboard, 2, 10);

      // Publish 3 facts and supersede 2, then check trigger
      var future =
          blackboard
              .publish("m1", "summary-1", null, 1)
              .compose(f1 -> blackboard.publish("m1", "summary-2", null, 1))
              .compose(f2 -> blackboard.publish("m1", "summary-3", null, 2))
              .compose(f3 -> blackboard.supersede("fact-1", "outdated"))
              .compose(v -> blackboard.supersede("fact-2", "outdated"))
              .compose(v -> trigger.shouldSummarize(3));

      assertFutureSuccess(
          future, ctx, result -> assertTrue(result, "Should trigger: 2 superseded >= threshold 2"));
    }

    @Test
    void doesNotTrigger_whenSupersededCountBelowThreshold(VertxTestContext ctx) {
      var trigger = new CycleAwareTrigger(blackboard, 5, 10);

      // Publish and supersede only 1, then check trigger
      var future =
          blackboard
              .publish("m1", "summary-1", null, 1)
              .compose(f -> blackboard.publish("m1", "summary-2", null, 1))
              .compose(f -> blackboard.supersede("fact-1", "outdated"))
              .compose(v -> trigger.shouldSummarize(2));

      assertFutureSuccess(
          future,
          ctx,
          result -> assertFalse(result, "Should not trigger: 1 superseded < threshold 5"));
    }
  }

  @Nested
  class CycleCountTrigger {

    @Test
    void triggers_whenCycleCountExceedsThreshold(VertxTestContext ctx) {
      var trigger = new CycleAwareTrigger(blackboard, 100, 5);

      assertFutureSuccess(
          trigger.shouldSummarize(5),
          ctx,
          result -> assertTrue(result, "Should trigger: cycle 5 >= threshold 5"));
    }

    @Test
    void doesNotTrigger_whenCycleCountBelowThreshold(VertxTestContext ctx) {
      var trigger = new CycleAwareTrigger(blackboard, 100, 10);

      assertFutureSuccess(
          trigger.shouldSummarize(3),
          ctx,
          result -> assertFalse(result, "Should not trigger: cycle 3 < threshold 10"));
    }
  }

  @Nested
  class CombinedTrigger {

    @Test
    void triggers_whenEitherConditionMet(VertxTestContext ctx) {
      // Low cycle threshold, high superseded threshold
      var trigger = new CycleAwareTrigger(blackboard, 100, 2);

      // No superseded facts, but cycle count exceeds threshold
      assertFutureSuccess(
          trigger.shouldSummarize(2),
          ctx,
          result -> assertTrue(result, "Should trigger on cycle count alone"));
    }

    @Test
    void doesNotTrigger_whenNeitherConditionMet(VertxTestContext ctx) {
      var trigger = new CycleAwareTrigger(blackboard, 100, 100);

      assertFutureSuccess(
          trigger.shouldSummarize(1),
          ctx,
          result -> assertFalse(result, "Should not trigger when both below threshold"));
    }
  }
}
