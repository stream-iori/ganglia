package work.ganglia.trading.prompt;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;
import work.ganglia.trading.config.TradingConfig;

/**
 * Defines the 5-layer trading workflow pipeline.
 *
 * <p>Maps to the architecture: Constitution → Perception → Research Crucible → Execution/Risk →
 * Evolution.
 */
public class TradingWorkflowSource implements ContextSource {

  private final TradingConfig config;

  public TradingWorkflowSource(TradingConfig config) {
    this.config = config;
  }

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    String content =
        """
        ## Trading Workflow Pipeline

        ### Layer 1: Perception (Parallel Analyst Scan)
        Four isolated analysts scan independently:
        - **Market Analyst**: Technical indicators (SMA/EMA/MACD/RSI/Bollinger/ATR/VWMA/MFI)
          → Market Regime Tags [Trending/Ranging], [High/Low Volatility]
        - **Fundamentals Analyst**: Balance Sheet + Cashflow + Income Statement
          → Valuation Tags [Undervalued/Overvalued]
        - **News Analyst**: Global macro + company catalysts + insider transactions
          → Event Tags [Earnings/FedReserve/Regulatory]
        - **Social Analyst**: Social sentiment + non-rational momentum signals

        All reports produced under sensory isolation. No cross-analyst visibility.

        ### Layer 2: Research Crucible (Adversarial Debate, %d rounds max)
        - Bull Researcher: builds long thesis from all 4 analyst reports
        - Bear Researcher: builds short thesis from same reports
        - Both have role-isolated BM25 memory for historical reinforcement
        - Research Manager (Invest Judge): synthesizes, scores, produces Investment Plan

        ### Layer 3: Execution & Risk Audit
        - Trader: position sizing and entry strategy from Investment Plan
        - Risk Team (%d rounds max): Aggressive / Neutral / Conservative auditors
        - Portfolio Manager: final 5-tier verdict (BUY/OVERWEIGHT/HOLD/UNDERWEIGHT/SELL)
        - Signal Processor: extracts structured signal from PM's narrative output

        ### Layer 4: Evolution & Memory
        - Reflector: 4-step audit per role (Reasoning → Improvement → Summary → Query)
        - Role-isolated BM25 memory with time-weighted retrieval (TWR)
        - Asymmetric injection: warnings weighted higher than successes

        ### Memory Integration
        When available, agents receive `historical_memories` containing lessons from similar
        past situations retrieved via BM25. These should be treated as high-priority context:
        - **Warnings and failure lessons** take precedence over success patterns
        - Apply historical insights to calibrate confidence and catch blind spots
        - Do not blindly follow past advice — validate against current data
        """
            .formatted(config.maxDebateRounds(), config.maxRiskDiscussRounds());

    return Future.succeededFuture(
        List.of(ContextFragment.prunable("trading-workflow", content, 5)));
  }
}
