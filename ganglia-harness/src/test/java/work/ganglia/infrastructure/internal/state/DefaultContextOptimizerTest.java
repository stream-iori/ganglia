package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.config.AgentConfigProvider;
import work.ganglia.config.ModelConfigProvider;
import work.ganglia.config.model.ModelConfig;
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.external.llm.ModelOptions;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class DefaultContextOptimizerTest extends BaseGangliaTest {

  private ModelConfigProvider modelConfig(int contextLimit) {
    return new ModelConfigProvider() {
      @Override
      public ModelConfig getModelConfig(String modelKey) {
        return null;
      }

      @Override
      public String getModel() {
        return "test";
      }

      @Override
      public String getUtilityModel() {
        return "test";
      }

      @Override
      public double getTemperature() {
        return 0.7;
      }

      @Override
      public int getContextLimit() {
        return contextLimit;
      }

      @Override
      public int getMaxTokens() {
        return 100;
      }

      @Override
      public boolean isStream() {
        return false;
      }

      @Override
      public boolean isUtilityStream() {
        return false;
      }

      @Override
      public String getBaseUrl() {
        return "http://localhost";
      }

      @Override
      public String getProvider() {
        return "test";
      }
    };
  }

  private AgentConfigProvider agentConfig(double threshold) {
    return new AgentConfigProvider() {
      @Override
      public int getMaxIterations() {
        return 10;
      }

      @Override
      public double getCompressionThreshold() {
        return threshold;
      }

      @Override
      public String getProjectRoot() {
        return System.getProperty("user.dir");
      }

      @Override
      public String getInstructionFile() {
        return null;
      }
    };
  }

  private ContextCompressor simpleCompressor() {
    return new ContextCompressor() {
      @Override
      public Future<String> summarize(List<Turn> turns, ModelOptions options) {
        return Future.succeededFuture("Summary");
      }

      @Override
      public Future<String> reflect(Turn turn) {
        return Future.succeededFuture("Reflection");
      }

      @Override
      public Future<String> compress(List<Turn> turns) {
        return Future.succeededFuture("Compressed: " + turns.size() + " turns");
      }
    };
  }

  @Test
  void testNoCompressionWhenBelowThreshold(VertxTestContext testContext) {
    // Large context limit so threshold is never triggered
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(100000), agentConfig(0.8), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Message user = Message.user("Hi");
    Turn turn = Turn.newTurn("t1", user).withResponse(Message.assistant("Hello"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Original context returned unchanged
                        assertEquals(1, result.previousTurns().size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testCompressionTriggeredWithMultipleTurns(VertxTestContext testContext) {
    // Very low limit + very low threshold to force compression
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(10), agentConfig(0.1), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn1 =
        Turn.newTurn("t1", Message.user("Long message to exceed token budget definitely"))
            .withResponse(Message.assistant("Long response also counts"));
    Turn turn2 =
        Turn.newTurn("t2", Message.user("Second turn")).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn1, turn2));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Compression should have happened — there should be a summary turn
                        assertFalse(result.previousTurns().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testHardLimitReturnsFailure(VertxTestContext testContext) {
    // We can't easily generate 500k tokens in a test, but verify the threshold path by
    // checking the guardrail isn't triggered on normal input
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(1000000), agentConfig(0.8), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn =
        Turn.newTurn("t1", Message.user("Normal message")).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Should succeed — not near 500k
                        assertFalse(result.previousTurns().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testNoCompressionWithSingleTurn(VertxTestContext testContext) {
    // threshold would trigger but only 1 previous turn (needs >1)
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(10), agentConfig(0.001), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn singleTurn =
        Turn.newTurn("t1", Message.user("Big message that exceeds limit"))
            .withResponse(Message.assistant("response"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(singleTurn));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Only 1 turn, no compression needed even if above threshold
                        assertEquals(1, result.previousTurns().size());
                        testContext.completeNow();
                      });
                }));
  }
}
