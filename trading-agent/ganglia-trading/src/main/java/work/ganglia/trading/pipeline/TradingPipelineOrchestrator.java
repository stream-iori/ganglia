package work.ganglia.trading.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;

import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.subagent.CyclicManagerEngine;
import work.ganglia.kernel.subagent.CyclicManagerEngine.CycleContext;
import work.ganglia.kernel.subagent.CyclicManagerEngine.EngineResult;
import work.ganglia.kernel.subagent.DefaultGraphExecutor;
import work.ganglia.kernel.subagent.TaskGraph;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.state.ObservationDispatcher;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.graph.DebateGraphBuilder;
import work.ganglia.trading.graph.DebateTerminationController;
import work.ganglia.trading.graph.PerceptionGraphBuilder;
import work.ganglia.trading.graph.RiskDebateGraphBuilder;
import work.ganglia.trading.graph.TraderGraphBuilder;
import work.ganglia.trading.signal.SignalExtractor;
import work.ganglia.trading.signal.SignalExtractor.TradingSignal;

/**
 * Orchestrates the full trading analysis pipeline: perception, debate, risk assessment, and signal
 * extraction.
 *
 * <p>Pipeline phases:
 *
 * <ol>
 *   <li>Perception: parallel analyst nodes + judge aggregation
 *   <li>Research Debate: iterative bull/bear debate with termination control
 *   <li>Risk Debate: iterative risk assessment with portfolio manager synthesis
 *   <li>Signal Extraction: parse the final output into a structured trading signal
 * </ol>
 */
public class TradingPipelineOrchestrator {
  private static final Logger logger = LoggerFactory.getLogger(TradingPipelineOrchestrator.class);

  /** Minimum cycles before stall detection activates. */
  private static final int STALL_THRESHOLD = 2;

  /** The result of a full pipeline execution. */
  public record PipelineResult(
      String perceptionReport,
      String debateReport,
      String traderReport,
      String riskReport,
      TradingSignal signal) {}

  private final TradingConfig config;
  private final AgentLoopFactory loopFactory;
  private final ObservationDispatcher dispatcher;
  private final SignalExtractor signalExtractor;

  public TradingPipelineOrchestrator(
      TradingConfig config,
      AgentLoopFactory loopFactory,
      ObservationDispatcher dispatcher,
      SignalExtractor signalExtractor) {
    this.config = config;
    this.loopFactory = loopFactory;
    this.dispatcher = dispatcher;
    this.signalExtractor = signalExtractor;
  }

  /**
   * Executes the full pipeline for the given ticker.
   *
   * @param ticker the stock ticker to analyze
   * @param parentContext the parent session context
   * @return the pipeline result including perception, debate, risk reports, and extracted signal
   */
  public Future<PipelineResult> execute(String ticker, SessionContext parentContext) {
    logger.info("Starting trading pipeline for ticker={}", ticker);

    return runPerception(ticker, parentContext)
        .compose(
            perceptionReport -> {
              logger.info("Phase 1 (Perception) complete, starting Phase 2 (Debate)");
              return runCyclicDebate(
                      new DebateGraphBuilder(config, perceptionReport),
                      config.maxDebateRounds(),
                      parentContext)
                  .map(result -> new String[] {perceptionReport, result.aggregatedReport()});
            })
        .compose(
            reports -> {
              logger.info("Phase 2 (Debate) complete, starting Phase 3 (Trader)");
              return runTrader(reports[1], parentContext)
                  .map(traderReport -> new String[] {reports[0], reports[1], traderReport});
            })
        .compose(
            reports -> {
              logger.info("Phase 3 (Trader) complete, starting Phase 4 (Risk)");
              return runCyclicDebate(
                      new RiskDebateGraphBuilder(config, reports[2]),
                      config.maxRiskDiscussRounds(),
                      parentContext)
                  .map(
                      result ->
                          buildResult(
                              reports[0], reports[1], reports[2], result.aggregatedReport()));
            });
  }

  private Future<String> runPerception(String ticker, SessionContext parentContext) {
    var graph = new PerceptionGraphBuilder().build(ticker);
    return new DefaultGraphExecutor(loopFactory).execute(graph, parentContext);
  }

  private Future<String> runTrader(String investmentPlan, SessionContext parentContext) {
    var graph = new TraderGraphBuilder(investmentPlan).build();
    return new DefaultGraphExecutor(loopFactory).execute(graph, parentContext);
  }

  private Future<EngineResult> runCyclicDebate(
      java.util.function.Function<CycleContext, TaskGraph> graphBuilder,
      int maxRounds,
      SessionContext parentContext) {
    var blackboard = new InMemoryBlackboard();
    var termination = new DebateTerminationController(blackboard, maxRounds, STALL_THRESHOLD);
    var engine =
        new CyclicManagerEngine(
            new DefaultGraphExecutor(loopFactory),
            blackboard,
            termination,
            dispatcher,
            new CyclicManagerEngine.EngineConfig(maxRounds));
    return engine.run(graphBuilder, parentContext);
  }

  private PipelineResult buildResult(
      String perceptionReport, String debateReport, String traderReport, String riskReport) {
    logger.info("Phase 4 (Risk) complete, extracting signal");
    TradingSignal signal = signalExtractor.extract(riskReport);
    logger.info(
        "Pipeline complete: signal={}, confidence={}", signal.signal(), signal.confidence());
    return new PipelineResult(perceptionReport, debateReport, traderReport, riskReport, signal);
  }
}
