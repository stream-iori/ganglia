package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Average True Range (ATR) indicator with period 14. */
public class AtrIndicator implements TechnicalIndicator {

  private static final int PERIOD = 14;

  @Override
  public String name() {
    return "atr";
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < PERIOD + 1) {
      return insufficientData(PERIOD + 1, filtered.size());
    }

    double[] tr = new double[filtered.size()];
    tr[0] = filtered.get(0).high() - filtered.get(0).low();
    for (int i = 1; i < filtered.size(); i++) {
      double hl = filtered.get(i).high() - filtered.get(i).low();
      double hpc = Math.abs(filtered.get(i).high() - filtered.get(i - 1).close());
      double lpc = Math.abs(filtered.get(i).low() - filtered.get(i - 1).close());
      tr[i] = Math.max(hl, Math.max(hpc, lpc));
    }

    double atrValue = 0;
    for (int i = 1; i <= PERIOD; i++) {
      atrValue += tr[i];
    }
    atrValue /= PERIOD;

    double[] atrValues = new double[filtered.size()];
    atrValues[PERIOD] = atrValue;

    for (int i = PERIOD + 1; i < filtered.size(); i++) {
      atrValue = (atrValue * (PERIOD - 1) + tr[i]) / PERIOD;
      atrValues[i] = atrValue;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("ATR(").append(PERIOD).append(") - Average True Range:\n");
    int start = outputStart(PERIOD, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      appendLine(sb, filtered.get(i).date(), atrValues[i]);
    }
    return sb.toString();
  }
}
