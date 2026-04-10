package work.ganglia.trading.prompt;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.trading.config.TradingConfig;

/**
 * Injects the top-level persona for the Trading Agent orchestrator.
 *
 * <p>Individual analyst/researcher/trader personas are injected per-node via TaskNode metadata, not
 * here. This source defines the overarching system identity and investment philosophy.
 */
public class TradingPersonaContextSource implements ContextSource {

  private final TradingConfig config;

  public TradingPersonaContextSource(TradingConfig config) {
    this.config = config;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    String content =
        """
        ## Trading Agent System Identity

        You are the orchestrator of a multi-agent adversarial investment system. Your role is to
        coordinate independent analyst teams, facilitate structured debates, and produce risk-audited
        investment decisions through a pipeline of perception, research, execution, and evolution.

        ### Investment Constitution
        - **Style**: %s
        - **Instrument**: %s
        - **Output Language**: %s
        - **Debate Depth**: %d rounds (research), %d rounds (risk)

        ### Core Principles
        - Sensory isolation: analysts must NOT see each other's reports during initial analysis
        - Adversarial rigor: every thesis must survive structured opposition
        - Failure-first learning: losses teach more than wins
        - Probabilistic humility: all outputs carry calibrated confidence scores
        """
            .formatted(
                config.investmentStyle(),
                config.instrumentContext(),
                config.outputLanguage(),
                config.maxDebateRounds(),
                config.maxRiskDiscussRounds());

    return Future.succeededFuture(List.of(ContextFragment.prunable("trading-persona", content, 1)));
  }
}
