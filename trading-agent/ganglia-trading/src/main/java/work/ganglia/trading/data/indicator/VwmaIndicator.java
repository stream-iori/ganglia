package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Volume Weighted Moving Average (VWMA) indicator with period 20. */
public class VwmaIndicator implements TechnicalIndicator {

  private static final int PERIOD = 20;

  @Override
  public String name() {
    return "vwma";
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < PERIOD) {
      return insufficientData(PERIOD, filtered.size());
    }

    StringBuilder sb = new StringBuilder();
    sb.append("VWMA(").append(PERIOD).append(") - Volume Weighted Moving Average:\n");

    int start = outputStart(PERIOD - 1, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      double sumPV = 0;
      double sumV = 0;
      for (int j = i - PERIOD + 1; j <= i; j++) {
        sumPV += filtered.get(j).close() * filtered.get(j).volume();
        sumV += filtered.get(j).volume();
      }
      double vwmaValue = sumV == 0 ? 0 : sumPV / sumV;
      appendLine(sb, filtered.get(i).date(), vwmaValue);
    }
    return sb.toString();
  }
}
