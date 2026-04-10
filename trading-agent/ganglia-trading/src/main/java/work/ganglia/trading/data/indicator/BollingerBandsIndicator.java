package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/**
 * Bollinger Bands indicator. Component determines output:
 *
 * <ul>
 *   <li>"boll" — Middle band (SMA20)
 *   <li>"boll_ub" — Upper band (SMA20 + 2 stddev)
 *   <li>"boll_lb" — Lower band (SMA20 - 2 stddev)
 * </ul>
 */
public class BollingerBandsIndicator implements TechnicalIndicator {

  private static final int PERIOD = 20;
  private final String component;

  public BollingerBandsIndicator(String component) {
    this.component = component;
  }

  @Override
  public String name() {
    return component;
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < PERIOD) {
      return insufficientData(PERIOD, filtered.size());
    }

    String label =
        switch (component) {
          case "boll_ub" -> "Bollinger Upper Band";
          case "boll_lb" -> "Bollinger Lower Band";
          default -> "Bollinger Middle Band";
        };
    StringBuilder sb = new StringBuilder();
    sb.append(label).append(" (").append(PERIOD).append("):\n");

    int start = outputStart(PERIOD - 1, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      double sum = 0;
      for (int j = i - PERIOD + 1; j <= i; j++) {
        sum += filtered.get(j).close();
      }
      double mean = sum / PERIOD;

      double variance = 0;
      for (int j = i - PERIOD + 1; j <= i; j++) {
        double diff = filtered.get(j).close() - mean;
        variance += diff * diff;
      }
      double stdDev = Math.sqrt(variance / PERIOD);

      double value =
          switch (component) {
            case "boll_ub" -> mean + 2 * stdDev;
            case "boll_lb" -> mean - 2 * stdDev;
            default -> mean;
          };
      appendLine(sb, filtered.get(i).date(), value);
    }
    return sb.toString();
  }
}
