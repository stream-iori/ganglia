package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Exponential Moving Average indicator with configurable period. */
public class EmaIndicator implements TechnicalIndicator {

  private final int period;
  private final String indicatorName;

  public EmaIndicator(int period, String indicatorName) {
    this.period = period;
    this.indicatorName = indicatorName;
  }

  @Override
  public String name() {
    return indicatorName;
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < period) {
      return insufficientData(period, filtered.size());
    }

    double multiplier = 2.0 / (period + 1);

    // Seed EMA with SMA of first 'period' values
    double ema = 0;
    for (int i = 0; i < period; i++) {
      ema += filtered.get(i).close();
    }
    ema /= period;

    double[] emaValues = new double[filtered.size()];
    emaValues[period - 1] = ema;
    for (int i = period; i < filtered.size(); i++) {
      ema = (filtered.get(i).close() - ema) * multiplier + ema;
      emaValues[i] = ema;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("EMA(").append(period).append(") - Exponential Moving Average:\n");
    int start = outputStart(period - 1, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      appendLine(sb, filtered.get(i).date(), emaValues[i]);
    }
    return sb.toString();
  }
}
