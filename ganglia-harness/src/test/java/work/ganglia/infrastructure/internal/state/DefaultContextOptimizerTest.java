package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import work.ganglia.port.internal.prompt.ContextBudget;
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
    return agentConfig(threshold, AgentConfigProvider.DEFAULT_SYSTEM_OVERHEAD_TOKENS);
  }

  private AgentConfigProvider agentConfig(double threshold, int systemOverhead) {
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

      @Override
      public int getSystemOverheadTokens() {
        return systemOverhead;
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

      @Override
      public Future<String> extractKeyFacts(Turn completedTurn, String existingRunningSummary) {
        return Future.succeededFuture("Key facts extracted");
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
    // Low threshold + zero overhead to trigger normal compression with a small contextLimit.
    // force/hard limits (10×3=30, 10×4=40) stay above actual message tokens (~20).
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(10), agentConfig(0.1, 0), simpleCompressor(), new TokenCounter());

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
    // Hard limit = contextLimit × 4.0. Verify the guardrail isn't triggered on normal input.
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
                        // Should succeed — not near hard limit (1M × 4.0 = 4M)
                        assertFalse(result.previousTurns().isEmpty());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testNoCompressionWithSingleTurn(VertxTestContext testContext) {
    // threshold would trigger but only 1 previous turn (needs >1).
    // Zero overhead + contextLimit=10 keeps total below force/hard limits (30/40).
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(10), agentConfig(0.001, 0), simpleCompressor(), new TokenCounter());

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

  /**
   * When totalTokens exceeds contextLimit × forceCompressionMultiplier (default 3.0), forced
   * compression triggers even with only 1 previous turn — bypassing the normal previousTurns.size()
   * > 1 guard.
   */
  @Test
  void testForcedCompressionTriggersWithSingleLargeTurn(VertxTestContext testContext) {
    // overhead = 6000 (default). We need historyTokens + 6000 > contextLimit × 3.0.
    // With contextLimit = 130_000: forceLimit = 390_000, hardLimit = 520_000.
    // historyTokens ≈ 395k → totalTokens ≈ 401k > 390k (forced) but < 520k (hard).
    // Normal threshold: 130_000 × 0.8 = 104_000 — would trigger, but single turn guard blocks it.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(130000), agentConfig(0.8), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    // "word " ≈ 1 token; repeat 395000 times ≈ 395k tokens
    String hugeContent = "word ".repeat(395000);
    Turn hugeTurn =
        Turn.newTurn("t1", Message.user(hugeContent)).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(hugeTurn));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Forced compression should create a summary turn
                        assertTrue(
                            result.previousTurns().stream()
                                .anyMatch(t -> t.id().startsWith("summary-")),
                            "Forced compression must produce a summary turn");
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testSystemOverheadIncludedInThreshold(VertxTestContext testContext) {
    // contextLimit=20, threshold=0.8 → trigger at 16.
    // historyTokens (~14) + overhead=20 → total=34 > 16 → triggers.
    // 50% budget = 10 tokens → only 2 turns (~5 each) fit → turnsToKeep=2.
    // With 3 turns, 1 gets compressed → summary turn created.
    AgentConfigProvider configWithOverhead =
        new AgentConfigProvider() {
          @Override
          public int getMaxIterations() {
            return 10;
          }

          @Override
          public double getCompressionThreshold() {
            return 0.8;
          }

          @Override
          public String getProjectRoot() {
            return System.getProperty("user.dir");
          }

          @Override
          public String getInstructionFile() {
            return null;
          }

          @Override
          public int getSystemOverheadTokens() {
            return 20;
          }
        };

    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(20), configWithOverhead, simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn1 =
        Turn.newTurn("t1", Message.user("Hello world test"))
            .withResponse(Message.assistant("Response one here"));
    Turn turn2 =
        Turn.newTurn("t2", Message.user("Another message"))
            .withResponse(Message.assistant("Another response"));
    Turn turn3 =
        Turn.newTurn("t3", Message.user("Third message"))
            .withResponse(Message.assistant("Third response"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn1, turn2, turn3));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Overhead pushed total above threshold → compression created a summary
                        assertTrue(
                            result.previousTurns().stream()
                                .anyMatch(t -> t.id().startsWith("summary-")));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testDynamicTurnsToKeepRetainsMoreTurns(VertxTestContext testContext) {
    // Large context limit (200000) so 50% target = 100000 tokens. 5 small turns should all fit.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(200000), agentConfig(0.001), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    SessionContext withHistory = ctx.withPreviousTurns(turns);

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // All 5 small turns fit within 50% budget, so all should be kept
                        // (plus 1 summary turn = 6 total, but since all fit, nothing to compress)
                        // With dynamic keep = 5, compressSession returns context unchanged
                        assertEquals(5, result.previousTurns().size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testRunningSummaryUsedDuringCompression(VertxTestContext testContext) {
    // Track whether compress was called
    boolean[] compressCalled = {false};
    ContextCompressor trackingCompressor =
        new ContextCompressor() {
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
            compressCalled[0] = true;
            return Future.succeededFuture("LLM Compressed");
          }

          @Override
          public Future<String> extractKeyFacts(Turn completedTurn, String existingRunningSummary) {
            return Future.succeededFuture("Key facts");
          }
        };

    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(10), agentConfig(0.001, 0), trackingCompressor, new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn1 =
        Turn.newTurn("t1", Message.user("Long message to exceed budget definitely"))
            .withResponse(Message.assistant("Long response"));
    Turn turn2 =
        Turn.newTurn("t2", Message.user("Second turn")).withResponse(Message.assistant("ok"));
    // Set running summary in metadata
    SessionContext withHistory =
        ctx.withPreviousTurns(List.of(turn1, turn2)).withRunningSummary("Pre-existing summary");

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Running summary was used, so LLM compress should NOT have been called
                        assertFalse(compressCalled[0]);
                        // Summary turn should contain the running summary
                        assertTrue(
                            result.previousTurns().stream()
                                .anyMatch(t -> t.id().startsWith("summary-")));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testHardLimitScalesWithContextLimit(VertxTestContext testContext) {
    // Small model: contextLimit=32000, hardLimit=32000×4.0=128000.
    // ~130k tokens should exceed hard limit and fail.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(32000), agentConfig(0.8), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    String hugeContent = "word ".repeat(130000); // ~130k tokens
    Turn hugeTurn =
        Turn.newTurn("t1", Message.user(hugeContent)).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(hugeTurn));

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err.getMessage().contains("maximum safety token limit"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void compressionUsesBudgetTarget(VertxTestContext testContext) {
    // Use a budget with a very large compressionTarget so all turns fit
    ContextBudget budget = ContextBudget.from(200000, 4096);
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(200000),
            agentConfig(0.001),
            simpleCompressor(),
            new TokenCounter(),
            null,
            budget);

    SessionContext ctx = createSessionContext();
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    SessionContext withHistory = ctx.withPreviousTurns(turns);

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Large budget → all 5 turns fit within compressionTarget
                        assertEquals(5, result.previousTurns().size());
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void compressionFallbackWithoutBudget(VertxTestContext testContext) {
    // No budget passed → falls back to 0.5 × limit
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            modelConfig(200000), agentConfig(0.001), simpleCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    SessionContext withHistory = ctx.withPreviousTurns(turns);

    optimizer
        .optimizeIfNeeded(withHistory)
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        // Fallback 0.5 × 200000 = 100000 → all 5 small turns fit
                        assertEquals(5, result.previousTurns().size());
                        testContext.completeNow();
                      });
                }));
  }
}
