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

import org.junit.jupiter.api.Nested;
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

  // ── constants for readability ──────────────────────────────────────────────

  private static final int TINY_CONTEXT = 10;
  private static final int SMALL_CONTEXT = 20;
  private static final int MEDIUM_CONTEXT = 32_000;
  private static final int LARGE_CONTEXT = 100_000;
  private static final int STANDARD_CONTEXT = 130_000;
  private static final int HUGE_CONTEXT = 200_000;
  private static final int MILLION_CONTEXT = 1_000_000;

  private static final double ALWAYS_TRIGGER = 0.001;
  private static final double NEARLY_ALWAYS_TRIGGER = 0.1;
  private static final double DEFAULT_THRESHOLD = 0.8;

  private static final int NO_OVERHEAD = 0;

  // ── mock factories ─────────────────────────────────────────────────────────

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

  private List<Turn> smallTurns(int count) {
    List<Turn> turns = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      turns.add(
          Turn.newTurn("t" + i, Message.user("msg" + i))
              .withResponse(Message.assistant("resp" + i)));
    }
    return turns;
  }

  // ── normal compression ─────────────────────────────────────────────────────

  @Nested
  class NormalCompression {

    @Test
    void optimizeIfNeeded_belowThreshold_returnsUnchanged(VertxTestContext testContext) {
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(LARGE_CONTEXT),
              mockAgentConfig(DEFAULT_THRESHOLD),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      Turn turn = Turn.newTurn("t1", Message.user("Hi")).withResponse(Message.assistant("Hello"));
      SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertEquals(1, result.previousTurns().size());
          });
    }

    @Test
    void optimizeIfNeeded_aboveThreshold_compresses(VertxTestContext testContext) {
      // Low threshold + zero overhead to trigger compression with a small contextLimit.
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(TINY_CONTEXT),
              mockAgentConfig(NEARLY_ALWAYS_TRIGGER, NO_OVERHEAD),
              mockCompressor(),
              new TokenCounter());

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
            assertFalse(result.previousTurns().isEmpty());
          });
    }

    @Test
    void optimizeIfNeeded_singleTurn_skipsCompression(VertxTestContext testContext) {
      // Threshold would trigger but only 1 previous turn (needs >1).
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(TINY_CONTEXT),
              mockAgentConfig(ALWAYS_TRIGGER, NO_OVERHEAD),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      Turn singleTurn =
          Turn.newTurn("t1", Message.user("Big message that exceeds limit"))
              .withResponse(Message.assistant("response"));
      SessionContext withHistory = ctx.withPreviousTurns(List.of(singleTurn));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertEquals(1, result.previousTurns().size());
          });
    }

    @Test
    void optimizeIfNeeded_systemOverhead_pushesAboveThreshold(VertxTestContext testContext) {
      // contextLimit=20, threshold=0.8 -> trigger at 16.
      // historyTokens (~14) + overhead=20 -> total=34 > 16 -> triggers.
      AgentConfigProvider configWithOverhead = mockAgentConfig(DEFAULT_THRESHOLD, 20);

      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(SMALL_CONTEXT),
              configWithOverhead,
              mockCompressor(),
              new TokenCounter());

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
            assertTrue(
                result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")));
          });
    }

    @Test
    void optimizeIfNeeded_runningSummary_skipsLLMCompression(VertxTestContext testContext) {
      ContextCompressor trackingCompressor = mock(ContextCompressor.class);
      when(trackingCompressor.summarize(any(), any()))
          .thenReturn(Future.succeededFuture("Summary"));
      when(trackingCompressor.reflect(any())).thenReturn(Future.succeededFuture("Reflection"));
      when(trackingCompressor.compress(any())).thenReturn(Future.succeededFuture("LLM Compressed"));
      when(trackingCompressor.extractKeyFacts(any(), any()))
          .thenReturn(Future.succeededFuture("Key facts"));

      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(TINY_CONTEXT),
              mockAgentConfig(ALWAYS_TRIGGER, NO_OVERHEAD),
              trackingCompressor,
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      Turn turn1 =
          Turn.newTurn("t1", Message.user("Long message to exceed budget definitely"))
              .withResponse(Message.assistant("Long response"));
      Turn turn2 =
          Turn.newTurn("t2", Message.user("Second turn")).withResponse(Message.assistant("ok"));
      SessionContext withHistory =
          ctx.withPreviousTurns(List.of(turn1, turn2)).withRunningSummary("Pre-existing summary");

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            verifyNoInteractions(trackingCompressor);
            assertTrue(
                result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")));
          });
    }
  }

  // ── dynamic keep ───────────────────────────────────────────────────────────

  @Nested
  class DynamicTurnsToKeep {

    @Test
    void optimizeIfNeeded_largeBudget_retainsAllSmallTurns(VertxTestContext testContext) {
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(HUGE_CONTEXT),
              mockAgentConfig(ALWAYS_TRIGGER),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      SessionContext withHistory = ctx.withPreviousTurns(smallTurns(5));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertEquals(5, result.previousTurns().size());
          });
    }
  }

  // ── forced / hard limits ───────────────────────────────────────────────────

  @Nested
  class Limits {

    @Test
    void optimizeIfNeeded_forcedCompression_singleLargeTurn(VertxTestContext testContext) {
      // contextLimit=130k: forceLimit=390k, hardLimit=520k.
      // historyTokens ~395k -> totalTokens ~401k > 390k (forced) but < 520k (hard).
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(STANDARD_CONTEXT),
              mockAgentConfig(DEFAULT_THRESHOLD),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      String hugeContent = "word ".repeat(395000);
      Turn hugeTurn =
          Turn.newTurn("t1", Message.user(hugeContent)).withResponse(Message.assistant("ok"));
      SessionContext withHistory = ctx.withPreviousTurns(List.of(hugeTurn));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertTrue(
                result.previousTurns().stream().anyMatch(t -> t.id().startsWith("summary-")),
                "Forced compression must produce a summary turn");
          });
    }

    @Test
    void optimizeIfNeeded_hardLimitNormal_succeeds(VertxTestContext testContext) {
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(MILLION_CONTEXT),
              mockAgentConfig(DEFAULT_THRESHOLD),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      Turn turn =
          Turn.newTurn("t1", Message.user("Normal message")).withResponse(Message.assistant("ok"));
      SessionContext withHistory = ctx.withPreviousTurns(List.of(turn));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertFalse(result.previousTurns().isEmpty());
          });
    }

    @Test
    void optimizeIfNeeded_hardLimitExceeded_failsWithError(VertxTestContext testContext) {
      // Small model: contextLimit=32k, hardLimit=32k*4.0=128k. ~130k tokens exceeds it.
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(MEDIUM_CONTEXT),
              mockAgentConfig(DEFAULT_THRESHOLD),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      String hugeContent = "word ".repeat(130000);
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
  }

  // ── budget integration ─────────────────────────────────────────────────────

  @Nested
  class BudgetIntegration {

    @Test
    void optimizeIfNeeded_withBudget_usesCompressionTarget(VertxTestContext testContext) {
      ContextBudget budget = ContextBudget.from(HUGE_CONTEXT, 4096);
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(HUGE_CONTEXT),
              mockAgentConfig(ALWAYS_TRIGGER),
              mockCompressor(),
              new TokenCounter(),
              null,
              budget);

      SessionContext ctx = createSessionContext();
      SessionContext withHistory = ctx.withPreviousTurns(smallTurns(5));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertEquals(5, result.previousTurns().size());
          });
    }

    @Test
    void optimizeIfNeeded_withoutBudget_fallsBackToHalfLimit(VertxTestContext testContext) {
      DefaultContextOptimizer optimizer =
          new DefaultContextOptimizer(
              mockModelConfig(HUGE_CONTEXT),
              mockAgentConfig(ALWAYS_TRIGGER),
              mockCompressor(),
              new TokenCounter());

      SessionContext ctx = createSessionContext();
      SessionContext withHistory = ctx.withPreviousTurns(smallTurns(5));

      assertFutureSuccess(
          optimizer.optimizeIfNeeded(withHistory),
          testContext,
          result -> {
            assertEquals(5, result.previousTurns().size());
          });
    }
  }
}
