package work.ganglia.trading.pipeline;

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
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator.PipelineResult;
import work.ganglia.trading.signal.SignalExtractor;
import work.ganglia.trading.signal.SignalExtractor.Signal;

@ExtendWith(VertxExtension.class)
class TradingPipelineOrchestratorTest extends BaseGangliaTest {

  private TradingConfig config;
  private SessionContext context;

  @BeforeEach
  void setUp(Vertx vertx) {
    config = TradingConfig.defaults();
    context = createSessionContext();
  }

  @Nested
  class FullPipeline {

    @Test
    void executesAllPhases_andReturnsResult(VertxTestContext testContext) {
      var orchestrator =
          new TradingPipelineOrchestrator(
              config, smartStubLoopFactory(), noOpDispatcher(), new SignalExtractor());

      Future<PipelineResult> future = orchestrator.execute("AAPL", context);

      assertFutureSuccess(
          future,
          testContext,
          result -> {
            assertNotNull(result);
            assertNotNull(result.perceptionReport());
            assertFalse(result.perceptionReport().isEmpty());
            assertNotNull(result.debateReport());
            assertFalse(result.debateReport().isEmpty());
            assertNotNull(result.riskReport());
            assertFalse(result.riskReport().isEmpty());
            assertNotNull(result.signal());
          });
    }

    @Test
    void returnsValidSignal(VertxTestContext testContext) {
      var orchestrator =
          new TradingPipelineOrchestrator(
              config, smartStubLoopFactory(), noOpDispatcher(), new SignalExtractor());

      Future<PipelineResult> future = orchestrator.execute("AAPL", context);

      assertFutureSuccess(
          future,
          testContext,
          result -> {
            assertNotNull(result.signal());
            assertEquals(Signal.BUY, result.signal().signal());
            assertEquals(0.85, result.signal().confidence(), 0.001);
          });
    }
  }

  @Nested
  class FailureHandling {

    @Test
    void failsGracefully_whenPerceptionFails(VertxTestContext testContext) {
      var orchestrator =
          new TradingPipelineOrchestrator(
              config, failingLoopFactory(), noOpDispatcher(), new SignalExtractor());

      Future<PipelineResult> future = orchestrator.execute("AAPL", context);

      assertFutureFailure(
          future,
          testContext,
          error -> {
            assertNotNull(error);
            assertInstanceOf(RuntimeException.class, error);
          });
    }
  }
}
