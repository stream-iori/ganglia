package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Simple Moving Average indicator with configurable period. */
public class SmaIndicator implements TechnicalIndicator {

  private final int period;
  private final String indicatorName;

  public SmaIndicator(int period, String indicatorName) {
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

    StringBuilder sb = new StringBuilder();
    sb.append("SMA(").append(period).append(") - Simple Moving Average:\n");

    int start = outputStart(period - 1, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      double sum = 0;
      for (int j = i - period + 1; j <= i; j++) {
        sum += filtered.get(j).close();
      }
      appendLine(sb, filtered.get(i).date(), sum / period);
    }
    return sb.toString();
  }
}
