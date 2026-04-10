package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Relative Strength Index (RSI) indicator with period 14. */
public class RsiIndicator implements TechnicalIndicator {

  private static final int PERIOD = 14;

  @Override
  public String name() {
    return "rsi";
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < PERIOD + 1) {
      return insufficientData(PERIOD + 1, filtered.size());
    }

    double[] gains = new double[filtered.size()];
    double[] losses = new double[filtered.size()];
    for (int i = 1; i < filtered.size(); i++) {
      double change = filtered.get(i).close() - filtered.get(i - 1).close();
      gains[i] = Math.max(change, 0);
      losses[i] = Math.max(-change, 0);
    }

    double avgGain = 0, avgLoss = 0;
    for (int i = 1; i <= PERIOD; i++) {
      avgGain += gains[i];
      avgLoss += losses[i];
    }
    avgGain /= PERIOD;
    avgLoss /= PERIOD;

    double[] rsiValues = new double[filtered.size()];
    rsiValues[PERIOD] = avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));

    for (int i = PERIOD + 1; i < filtered.size(); i++) {
      avgGain = (avgGain * (PERIOD - 1) + gains[i]) / PERIOD;
      avgLoss = (avgLoss * (PERIOD - 1) + losses[i]) / PERIOD;
      rsiValues[i] = avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
    }

    StringBuilder sb = new StringBuilder();
    sb.append("RSI(").append(PERIOD).append(") - Relative Strength Index:\n");
    int start = outputStart(PERIOD, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      appendLine(sb, filtered.get(i).date(), rsiValues[i]);
    }
    return sb.toString();
  }
}
