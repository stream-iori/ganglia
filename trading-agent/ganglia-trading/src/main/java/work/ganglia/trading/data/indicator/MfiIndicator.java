package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Money Flow Index (MFI) indicator with period 14. */
public class MfiIndicator implements TechnicalIndicator {

  private static final int PERIOD = 14;

  @Override
  public String name() {
    return "mfi";
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < PERIOD + 1) {
      return insufficientData(PERIOD + 1, filtered.size());
    }

    double[] tp = new double[filtered.size()];
    double[] mf = new double[filtered.size()];
    for (int i = 0; i < filtered.size(); i++) {
      tp[i] = (filtered.get(i).high() + filtered.get(i).low() + filtered.get(i).close()) / 3;
      mf[i] = tp[i] * filtered.get(i).volume();
    }

    StringBuilder sb = new StringBuilder();
    sb.append("MFI(").append(PERIOD).append(") - Money Flow Index:\n");

    int start = outputStart(PERIOD, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      double posFlow = 0;
      double negFlow = 0;
      for (int j = i - PERIOD + 1; j <= i; j++) {
        if (tp[j] > tp[j - 1]) {
          posFlow += mf[j];
        } else if (tp[j] < tp[j - 1]) {
          negFlow += mf[j];
        }
      }
      double mfiValue = negFlow == 0 ? 100 : 100 - (100 / (1 + posFlow / negFlow));
      appendLine(sb, filtered.get(i).date(), mfiValue);
    }
    return sb.toString();
  }
}
