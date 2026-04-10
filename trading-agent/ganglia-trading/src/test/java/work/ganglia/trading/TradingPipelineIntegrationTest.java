package work.ganglia.trading;

import static org.junit.jupiter.api.Assertions.*;
import static work.ganglia.trading.StubAgentLoopSupport.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator.PipelineResult;
import work.ganglia.trading.signal.SignalExtractor;
import work.ganglia.trading.signal.SignalExtractor.Signal;

/**
 * End-to-end integration smoke test for the trading pipeline. Wires ALL real components
 * (DefaultGraphExecutor, CyclicManagerEngine, InMemoryBlackboard, DebateTerminationController) with
 * a StubAgentLoop to verify the full pipeline runs without real LLM calls.
 *
 * <p>Since no real LLM produces output, the DebateTerminationController never detects stance
 * convergence, so debate/risk phases run to BUDGET_EXCEEDED (max rounds). This is expected for a
 * smoke test — we only verify the pipeline completes and produces a valid signal.
 */
@ExtendWith(VertxExtension.class)
class TradingPipelineIntegrationTest extends BaseGangliaTest {

  /** Use small cycle counts to keep the smoke test fast. */
  private static final TradingConfig FAST_CONFIG =
      new TradingConfig(
          TradingConfig.InvestmentStyle.VALUE,
          2, // maxDebateRounds
          1, // maxRiskDiscussRounds
          "en",
          "stock",
          TradingConfig.DataVendor.YFINANCE,
          TradingConfig.DataVendor.ALPHA_VANTAGE,
          false,
          180,
          ".ganglia/trading-cache");

  private SessionContext context;

  @BeforeEach
  void setUp(Vertx vertx) {
    context = createSessionContext();
  }

  @Nested
  class EndToEnd {

    @Test
    void fullPipeline_producesSignal_withStubLoop(VertxTestContext testContext) {
      var orchestrator =
          new TradingPipelineOrchestrator(
              FAST_CONFIG, smartStubLoopFactory(), noOpDispatcher(), new SignalExtractor());

      Future<PipelineResult> future = orchestrator.execute("AAPL", context);

      assertFutureSuccess(
          future,
          testContext,
          result -> {
            assertNotNull(result, "Pipeline result must not be null");

            assertNotNull(result.perceptionReport(), "Perception report must not be null");
            assertFalse(result.perceptionReport().isEmpty(), "Perception report must not be empty");

            assertNotNull(result.debateReport(), "Debate report must not be null");
            assertFalse(result.debateReport().isEmpty(), "Debate report must not be empty");

            assertNotNull(result.traderReport(), "Trader report must not be null");
            assertFalse(result.traderReport().isEmpty(), "Trader report must not be empty");

            assertNotNull(result.riskReport(), "Risk report must not be null");
            assertFalse(result.riskReport().isEmpty(), "Risk report must not be empty");

            assertNotNull(result.signal(), "Extracted signal must not be null");
            assertEquals(
                Signal.BUY,
                result.signal().signal(),
                "Signal should be BUY from PM's canned output");
            assertEquals(
                0.85,
                result.signal().confidence(),
                0.001,
                "Confidence should be 0.85 from PM's canned output");
          });
    }

    @Test
    void pipelineCompletes_withinBudget(VertxTestContext testContext) {
      var orchestrator =
          new TradingPipelineOrchestrator(
              FAST_CONFIG, smartStubLoopFactory(), noOpDispatcher(), new SignalExtractor());

      Future<PipelineResult> future = orchestrator.execute("AAPL", context);

      assertFutureSuccess(
          future,
          testContext,
          result -> {
            assertNotNull(result, "Pipeline must complete and produce a result");
            assertNotNull(result.signal(), "Pipeline must produce a signal even at budget limit");
          });
    }
  }
}
