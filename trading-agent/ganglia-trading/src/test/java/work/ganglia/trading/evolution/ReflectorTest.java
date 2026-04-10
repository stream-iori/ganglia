package work.ganglia.trading.evolution;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.kernel.loop.AgentLoop;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.memory.TradingMemoryStore;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator.PipelineResult;
import work.ganglia.trading.signal.SignalExtractor.Signal;
import work.ganglia.trading.signal.SignalExtractor.TradingSignal;

class ReflectorTest extends BaseGangliaTest {

  private TradingMemoryStore memoryStore;
  private Reflector reflector;
  private SessionContext context;

  @BeforeEach
  void setUp(Vertx vertx) {
    setUpBase(vertx);
    memoryStore = new TradingMemoryStore(TradingConfig.defaults());
    reflector = new Reflector();
    context = createSessionContext();
  }

  private PipelineResult sampleResult() {
    return new PipelineResult(
        "Market is bullish with strong momentum",
        "Bull thesis: growth is solid. Bear thesis: overvalued.",
        "Buy 100 shares at market price.",
        "**Final Verdict: BUY**\n**Confidence: 0.85**",
        new TradingSignal(Signal.BUY, 0.85, "Strong fundamentals."));
  }

  private AgentLoopFactory reflectionLoopFactory() {
    return () ->
        new AgentLoop() {
          @Override
          public Future<String> run(String userInput, SessionContext ctx, AgentSignal signal) {
            return Future.succeededFuture(
                "REASONING: The decision was based on momentum indicators.\n"
                    + "IMPROVEMENT: Weight fundamentals more heavily in volatile markets.\n"
                    + "SUMMARY: When momentum is strong but fundamentals are weak, reduce position"
                    + " size because momentum can reverse quickly.\n"
                    + "QUERY: strong momentum weak fundamentals position sizing");
          }

          @Override
          public Future<String> resume(
              String askId, String toolOutput, SessionContext ctx, AgentSignal signal) {
            return Future.succeededFuture("resumed");
          }

          @Override
          public void stop(String sessionId) {}
        };
  }

  @Nested
  class ReflectAllRoles {

    @Test
    void storesLessonsForAllReflectiveRoles(VertxTestContext testContext) {
      assertFutureSuccess(
          reflector.reflect(
              sampleResult(), "+5% gain on AAPL", memoryStore, reflectionLoopFactory(), context),
          testContext,
          v -> {
            // All 5 reflective roles should have memories stored
            assertNotNull(memoryStore.forRole("BULL_RESEARCHER"));
            assertEquals(1, memoryStore.forRole("BULL_RESEARCHER").size());

            assertNotNull(memoryStore.forRole("BEAR_RESEARCHER"));
            assertEquals(1, memoryStore.forRole("BEAR_RESEARCHER").size());

            assertNotNull(memoryStore.forRole("TRADER"));
            assertEquals(1, memoryStore.forRole("TRADER").size());

            assertNotNull(memoryStore.forRole("INVEST_JUDGE"));
            assertEquals(1, memoryStore.forRole("INVEST_JUDGE").size());

            assertNotNull(memoryStore.forRole("PORTFOLIO_MANAGER"));
            assertEquals(1, memoryStore.forRole("PORTFOLIO_MANAGER").size());
          });
    }

    @Test
    void storedLessons_containSummaryContent(VertxTestContext testContext) {
      assertFutureSuccess(
          reflector.reflect(
              sampleResult(), "+5% gain on AAPL", memoryStore, reflectionLoopFactory(), context),
          testContext,
          v -> {
            var matches =
                memoryStore.forRole("BULL_RESEARCHER").retrieve("momentum fundamentals", 1);
            assertFalse(matches.isEmpty());
            assertTrue(matches.get(0).advice().contains("momentum"));
          });
    }

    @Test
    void storedLessons_useQueryAsSituation(VertxTestContext testContext) {
      assertFutureSuccess(
          reflector.reflect(
              sampleResult(), "+5% gain on AAPL", memoryStore, reflectionLoopFactory(), context),
          testContext,
          v -> {
            // The QUERY line becomes the situation key for BM25 retrieval
            var matches =
                memoryStore
                    .forRole("TRADER")
                    .retrieve("strong momentum weak fundamentals position sizing", 1);
            assertFalse(matches.isEmpty());
            assertTrue(matches.get(0).score() > 0.5, "Should have high relevance for exact query");
          });
    }
  }

  @Nested
  class ErrorHandling {

    @Test
    void continuesOnRoleFailure(VertxTestContext testContext) {
      // Shared counter across all loops created by this factory
      java.util.concurrent.atomic.AtomicInteger callCount =
          new java.util.concurrent.atomic.AtomicInteger(0);

      AgentLoopFactory mixedFactory =
          () ->
              new AgentLoop() {
                @Override
                public Future<String> run(
                    String userInput, SessionContext ctx, AgentSignal signal) {
                  if (callCount.incrementAndGet() == 1) {
                    return Future.failedFuture(new RuntimeException("LLM timeout"));
                  }
                  return Future.succeededFuture(
                      "REASONING: Analysis\n"
                          + "IMPROVEMENT: Adjust\n"
                          + "SUMMARY: Lesson learned.\n"
                          + "QUERY: test query");
                }

                @Override
                public Future<String> resume(
                    String askId, String toolOutput, SessionContext ctx, AgentSignal signal) {
                  return Future.succeededFuture("resumed");
                }

                @Override
                public void stop(String sessionId) {}
              };

      // Should still complete even though one role fails
      assertFutureSuccess(
          reflector.reflect(sampleResult(), "-2% loss", memoryStore, mixedFactory, context),
          testContext,
          v -> {
            // At least some roles should have stored memories (one failed, 4 succeeded)
            int total = 0;
            for (String persona :
                new String[] {
                  "BULL_RESEARCHER",
                  "BEAR_RESEARCHER",
                  "TRADER",
                  "INVEST_JUDGE",
                  "PORTFOLIO_MANAGER"
                }) {
              var mem = memoryStore.forRole(persona);
              if (mem != null) total += mem.size();
            }
            assertTrue(total >= 4, "4 of 5 roles should have stored reflections");
          });
    }
  }

  @Nested
  class PromptParsing {

    @Test
    void extractSummary_fromValidOutput() {
      String output =
          "REASONING: Good analysis\nIMPROVEMENT: Better\nSUMMARY: Key lesson here.\nQUERY: test";
      assertEquals("Key lesson here.", ReflectorPrompts.extractSummary(output));
    }

    @Test
    void extractQuery_fromValidOutput() {
      String output =
          "REASONING: Good analysis\nIMPROVEMENT: Better\nSUMMARY: Lesson\nQUERY: market crash recovery";
      assertEquals("market crash recovery", ReflectorPrompts.extractQuery(output));
    }

    @Test
    void extractSummary_fallsBackToTruncatedOutput() {
      String output = "Some unstructured reflection without markers";
      String summary = ReflectorPrompts.extractSummary(output);
      assertEquals(output, summary);
    }

    @Test
    void extractQuery_returnsEmpty_whenMissing() {
      String output = "REASONING: Analysis\nSUMMARY: Lesson";
      assertEquals("", ReflectorPrompts.extractQuery(output));
    }
  }
}
