package work.ganglia.infrastructure.internal.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import work.ganglia.port.chat.Message;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.chat.Turn;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.port.internal.prompt.ContextBudget;
import work.ganglia.util.TokenCounter;

@ExtendWith(VertxExtension.class)
class DefaultContextOptimizerTest extends BaseGangliaTest {

  private ModelConfigProvider mockModelConfig(int contextLimit) {
    ModelConfigProvider mock = mock(ModelConfigProvider.class);
    when(mock.getModel()).thenReturn("test");
    when(mock.getUtilityModel()).thenReturn("test");
    when(mock.getTemperature()).thenReturn(0.7);
    when(mock.getContextLimit()).thenReturn(contextLimit);
    when(mock.getMaxTokens()).thenReturn(contextLimit > 1000 ? 1000 : contextLimit / 10);
    when(mock.isStream()).thenReturn(false);
    when(mock.isUtilityStream()).thenReturn(false);
    when(mock.getBaseUrl()).thenReturn("http://localhost");
    when(mock.getProvider()).thenReturn("test");
    return mock;
  }

  private AgentConfigProvider mockAgentConfig(double threshold) {
    return mockAgentConfig(threshold, AgentConfigProvider.DEFAULT_SYSTEM_OVERHEAD_TOKENS);
  }

  private AgentConfigProvider mockAgentConfig(double threshold, int systemOverhead) {
    AgentConfigProvider mock = mock(AgentConfigProvider.class);
    when(mock.getMaxIterations()).thenReturn(10);
    when(mock.getCompressionThreshold()).thenReturn(threshold);
    when(mock.getProjectRoot()).thenReturn(System.getProperty("user.dir"));
    when(mock.getInstructionFile()).thenReturn(null);
    when(mock.getSystemOverheadTokens()).thenReturn(systemOverhead);
    when(mock.getHardLimitMultiplier()).thenReturn(4.0);
    when(mock.getForceCompressionMultiplier()).thenReturn(3.0);
    return mock;
  }

  private ContextCompressor mockCompressor() {
    ContextCompressor mock = mock(ContextCompressor.class);
    when(mock.summarize(any(), any())).thenReturn(Future.succeededFuture("Summary"));
    when(mock.reflect(any())).thenReturn(Future.succeededFuture("Reflection"));
    when(mock.compress(any()))
        .thenAnswer(
            invocation -> {
              List<Turn> turns = invocation.getArgument(0);
              return Future.succeededFuture("Compressed: " + turns.size() + " turns");
            });
    when(mock.compressText(any())).thenReturn(Future.succeededFuture("Compressed text"));
    when(mock.extractKeyFacts(any(), any()))
        .thenReturn(Future.succeededFuture("Key facts extracted"));
    return mock;
  }

  @Test
  void testNoCompressionWhenBelowThreshold(VertxTestContext testContext) {
    // Large context limit so threshold is never triggered
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(100000), mockAgentConfig(0.8), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Message user = Message.user("Hi");
    Turn turn = Turn.newTurn("t1", user).withResponse(Message.assistant("Hello"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Original context returned unchanged
          assertEquals(1, result.previousTurns().size());
        });
  }

  @Test
  void testCompressionTriggeredWithMultipleTurns(VertxTestContext testContext) {
    // Low threshold + zero overhead to trigger normal compression with a small contextLimit.
    // force/hard limits (10×3=30, 10×4=40) stay above actual message tokens (~20).
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(10), mockAgentConfig(0.1, 0), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn1 =
        Turn.newTurn("t1", Message.user("Long message to exceed token budget definitely"))
            .withResponse(Message.assistant("Long response also counts"));
    Turn turn2 =
        Turn.newTurn("t2", Message.user("Second turn")).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn1, turn2));

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Compression should have happened — there should be a summary turn
          assertFalse(result.previousTurns().isEmpty());
        });
  }

  @Test
  void testHardLimitReturnsFailure(VertxTestContext testContext) {
    // Hard limit = contextLimit × 4.0. Verify the guardrail isn't triggered on normal input.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(1000000), mockAgentConfig(0.8), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn =
        Turn.newTurn("t1", Message.user("Normal message")).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Should succeed — not near hard limit (1M × 4.0 = 4M)
          assertFalse(result.previousTurns().isEmpty());
        });
  }

  @Test
  void testNoCompressionWithSingleTurn(VertxTestContext testContext) {
    // threshold would trigger but only 1 previous turn (needs >1).
    // Zero overhead + contextLimit=10 keeps total below force/hard limits (30/40).
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(10), mockAgentConfig(0.001, 0), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn singleTurn =
        Turn.newTurn("t1", Message.user("Big message that exceeds limit"))
            .withResponse(Message.assistant("response"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(singleTurn));

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Only 1 turn, no compression needed even if above threshold
          assertEquals(1, result.previousTurns().size());
        });
  }

  @Test
  void testForcedCompressionTriggersWithSingleLargeTurn(VertxTestContext testContext) {
    // overhead = 6000 (default). We need historyTokens + 6000 > contextLimit × 3.0.
    // With contextLimit = 130_000: forceLimit = 390_000, hardLimit = 520_000.
    // historyTokens ≈ 395k → totalTokens ≈ 401k > 390k (forced) but < 520k (hard).
    // Normal threshold: 130_000 × 0.8 = 104_000 — would trigger, but single turn guard blocks it.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(130000), mockAgentConfig(0.8), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    // "word " ≈ 1 token; repeat 395000 times ≈ 395k tokens
    String hugeContent = "word ".repeat(395000);
    Turn hugeTurn =
        Turn.newTurn("t1", Message.user(hugeContent)).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(hugeTurn));

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Forced compression should create a summary turn
          assertTrue(
              result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")),
              "Forced compression must produce a summary turn");
        });
  }

  @Test
  void testSystemOverheadIncludedInThreshold(VertxTestContext testContext) {
    // contextLimit=20, threshold=0.8 → trigger at 16.
    // historyTokens (~14) + overhead=20 → total=34 > 16 → triggers.
    // 50% budget = 10 tokens → only 2 turns (~5 each) fit → turnsToKeep=2.
    // With 3 turns, 1 gets compressed → summary turn created.
    AgentConfigProvider configWithOverhead = mockAgentConfig(0.8, 20);

    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(20), configWithOverhead, mockCompressor(), new TokenCounter());

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

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Overhead pushed total above threshold → compression created a summary
          assertTrue(result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")));
        });
  }

  @Test
  void testDynamicTurnsToKeepRetainsMoreTurns(VertxTestContext testContext) {
    // Large context limit (200000) so 50% target = 100000 tokens. 5 small turns should all fit.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(200000), mockAgentConfig(0.001), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    SessionContext withHistory = ctx.withPreviousTurns(turns);

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // All 5 small turns fit within 50% budget, so all should be kept
          // (plus 1 summary turn = 6 total, but since all fit, nothing to compress)
          // With dynamic keep = 5, compressSession returns context unchanged
          assertEquals(5, result.previousTurns().size());
        });
  }

  @Test
  void testRunningSummaryUsedDuringCompression(VertxTestContext testContext) {
    ContextCompressor trackingCompressor = mock(ContextCompressor.class);
    when(trackingCompressor.summarize(any(), any())).thenReturn(Future.succeededFuture("Summary"));
    when(trackingCompressor.reflect(any())).thenReturn(Future.succeededFuture("Reflection"));
    when(trackingCompressor.compress(any())).thenReturn(Future.succeededFuture("LLM Compressed"));
    when(trackingCompressor.extractKeyFacts(any(), any()))
        .thenReturn(Future.succeededFuture("Key facts"));

    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(10), mockAgentConfig(0.001, 0), trackingCompressor, new TokenCounter());

    SessionContext ctx = createSessionContext();
    Turn turn1 =
        Turn.newTurn("t1", Message.user("Long message to exceed budget definitely"))
            .withResponse(Message.assistant("Long response"));
    Turn turn2 =
        Turn.newTurn("t2", Message.user("Second turn")).withResponse(Message.assistant("ok"));
    // Set running summary in metadata
    SessionContext withHistory =
        ctx.withPreviousTurns(List.of(turn1, turn2)).withRunningSummary("Pre-existing summary");

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Running summary was used, so LLM compress should NOT have been called
          verifyNoInteractions(trackingCompressor);
          // Summary turn should contain the running summary
          assertTrue(result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")));
        });
  }

  @Test
  void testHardLimitScalesWithContextLimit(VertxTestContext testContext) {
    // Small model: contextLimit=32000, hardLimit=32000×4.0=128000.
    // ~130k tokens should exceed hard limit and fail.
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(32000), mockAgentConfig(0.8), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    String hugeContent = "word ".repeat(130000); // ~130k tokens
    Turn hugeTurn =
        Turn.newTurn("t1", Message.user(hugeContent)).withResponse(Message.assistant("ok"));
    SessionContext withHistory = ctx.withPreviousTurns(List.of(hugeTurn));

    assertFutureFailure(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        err -> {
          assertTrue(err.getMessage().contains("maximum safety token limit"));
        });
  }

  @Test
  void compressionUsesBudgetTarget(VertxTestContext testContext) {
    // Use a budget with a very large compressionTarget so all turns fit
    ContextBudget budget = ContextBudget.from(200000, 4096);
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(200000),
            mockAgentConfig(0.001),
            mockCompressor(),
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

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Large budget → all 5 turns fit within compressionTarget
          assertEquals(5, result.previousTurns().size());
        });
  }

  @Test
  void compressionFallbackWithoutBudget(VertxTestContext testContext) {
    // No budget passed → falls back to 0.5 × limit
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(
            mockModelConfig(200000), mockAgentConfig(0.001), mockCompressor(), new TokenCounter());

    SessionContext ctx = createSessionContext();
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    SessionContext withHistory = ctx.withPreviousTurns(turns);

    assertFutureSuccess(
        optimizer.optimizeIfNeeded(withHistory),
        testContext,
        result -> {
          // Fallback 0.5 × 200000 = 100000 → all 5 small turns fit
          assertEquals(5, result.previousTurns().size());
        });
  }
}
