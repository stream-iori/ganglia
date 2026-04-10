package work.ganglia.trading.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

import work.ganglia.BootstrapOptions;
import work.ganglia.Ganglia;
import work.ganglia.infrastructure.internal.state.InMemoryBlackboard;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.observability.ObservabilityVerticle;
import work.ganglia.trading.TradingAgentBuilder;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.pipeline.TradingPipelineOrchestrator;
import work.ganglia.trading.signal.SignalExtractor;
import work.ganglia.trajectory.trace.TraceManager;

/** Main entry point for the Trading Agent WebUI server. */
public class TradingWebMain {
  private static final Logger logger = LoggerFactory.getLogger(TradingWebMain.class);

  private static final int DEFAULT_PORT = 9080;
  private static final int OBSERVABILITY_PORT = 9081;

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    TradingEventPublisher eventPublisher = new TradingEventPublisher(vertx);
    TradingConfig tradingConfig = TradingConfig.defaults();

    BootstrapOptions options = buildBootstrapOptions(eventPublisher);

    TradingAgentBuilder.create(vertx)
        .withOptions(options)
        .withTradingConfig(tradingConfig)
        .bootstrap()
        .compose(ganglia -> deployServices(vertx, ganglia, tradingConfig, eventPublisher))
        .onFailure(
            err -> {
              logger.error("Failed to start Trading Agent", err);
              System.exit(1);
            });
  }

  private static BootstrapOptions buildBootstrapOptions(TradingEventPublisher eventPublisher) {
    String configPath = System.getProperty("ganglia.config", ".ganglia/config.json");
    return BootstrapOptions.builder()
        .configPath(configPath)
        .extraObservers(List.of(eventPublisher))
        .traceWriterFactory(TraceManager::new)
        .build();
  }

  private static Future<Void> deployServices(
      Vertx vertx,
      Ganglia ganglia,
      TradingConfig tradingConfig,
      TradingEventPublisher eventPublisher) {
    Runtime.getRuntime().addShutdownHook(new Thread(ganglia::shutdown));

    InMemoryBlackboard blackboard = new InMemoryBlackboard();
    var dispatcher = new DefaultObservationDispatcher(vertx);
    dispatcher.register(eventPublisher);

    TradingPipelineOrchestrator orchestrator =
        new TradingPipelineOrchestrator(
            tradingConfig, () -> ganglia.agentLoop(), dispatcher, new SignalExtractor());

    int port = Integer.getInteger("trading.web.port", DEFAULT_PORT);
    TradingWebVerticle webVerticle =
        new TradingWebVerticle(port, null, orchestrator, tradingConfig, blackboard);

    ObservabilityVerticle obsVerticle =
        new ObservabilityVerticle(OBSERVABILITY_PORT, null, ".ganglia/trace");

    return vertx
        .deployVerticle(webVerticle)
        .onSuccess(id -> logger.info("Trading WebUI deployed on port {}", port))
        .compose(
            v ->
                vertx
                    .deployVerticle(obsVerticle)
                    .onSuccess(
                        id ->
                            logger.info(
                                "Observability Studio deployed on port {}", OBSERVABILITY_PORT)))
        .mapEmpty();
  }
}
