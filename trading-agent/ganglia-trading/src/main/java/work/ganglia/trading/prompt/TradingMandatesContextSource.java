package work.ganglia.trading.prompt;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.internal.prompt.ContextFragment;
import work.ganglia.port.internal.prompt.ContextSource;

/**
 * Non-negotiable guardrails for the trading system, derived from the architecture's "Implementation
 * Guardrails" (Section III).
 */
public class TradingMandatesContextSource implements ContextSource {

  @Override
  public Future<List<ContextFragment>> getFragments(SessionContext sessionContext) {
    String content =
        """
        ## Trading System Guardrails (Non-Negotiable)

        1. **Look-Ahead Bias Zero Tolerance**: ALL historical data MUST be filtered by curr_date.
           No future data may leak into analysis under any circumstance.
        2. **Sensory Isolation**: Analysts in Layer 1 must NOT access each other's outputs.
           Cross-pollination only allowed via controlled Regime Tag sharing in second pass.
        3. **Adversarial Isolation**: Bull and Bear researchers must NOT share context within
           the same debate round. Each builds arguments independently.
        4. **Memory Role Isolation**: Bull/Bear/Trader/Judge/PM memories are strictly separated.
           No cross-role memory contamination.
        5. **Failure-First Attribution**: Reflector MUST record and attribute negative-return
           cases. "Report only wins" is a system defect.
        6. **Asymmetric Memory Injection**: Historical warnings carry higher token weight than
           historical successes in prompt injection.
        7. **Confidence Calibration**: All agent outputs must include confidence scores.
           Post-hoc audit aligns confidence with actual regime hit rates.
        8. **Signal Normalization**: PM output MUST pass through Signal Processor to produce
           a standardized 5-tier rating before downstream consumption.
        9. **Data Supply Continuity**: Analysis must not halt due to single vendor failure.
           Automatic fallback to secondary data source on rate limits.
        """;

    return Future.succeededFuture(
        List.of(ContextFragment.prunable("trading-mandates", content, 2)));
  }
}
