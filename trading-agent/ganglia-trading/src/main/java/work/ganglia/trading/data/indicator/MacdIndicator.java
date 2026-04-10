package work.ganglia.trading.data.indicator;

import static work.ganglia.trading.data.indicator.IndicatorSupport.*;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/**
 * MACD indicator. Component determines output:
 *
 * <ul>
 *   <li>"macd" — MACD line (EMA12 - EMA26)
 *   <li>"macds" — Signal line (EMA9 of MACD)
 *   <li>"macdh" — Histogram (MACD - Signal)
 * </ul>
 */
public class MacdIndicator implements TechnicalIndicator {

  private static final int MIN_REQUIRED = 26 + 9;
  private final String component;

  public MacdIndicator(String component) {
    this.component = component;
  }

  @Override
  public String name() {
    return component;
  }

  @Override
  public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
    List<OhlcvRow> filtered = filterByDate(data, currDate);
    if (filtered.size() < MIN_REQUIRED) {
      return insufficientData(MIN_REQUIRED, filtered.size());
    }

    double[] closes = filtered.stream().mapToDouble(OhlcvRow::close).toArray();
    double[] ema12 = computeEmaArray(closes, 12);
    double[] ema26 = computeEmaArray(closes, 26);

    double[] macdLine = new double[closes.length];
    for (int i = 25; i < closes.length; i++) {
      macdLine[i] = ema12[i] - ema26[i];
    }

    int signalStart = 25 + 9 - 1;
    double[] signal = new double[closes.length];
    double seed = 0;
    for (int i = 25; i < 25 + 9; i++) {
      seed += macdLine[i];
    }
    signal[signalStart] = seed / 9;
    double multiplier = 2.0 / (9 + 1);
    for (int i = signalStart + 1; i < closes.length; i++) {
      signal[i] = (macdLine[i] - signal[i - 1]) * multiplier + signal[i - 1];
    }

    String label =
        switch (component) {
          case "macds" -> "MACD Signal";
          case "macdh" -> "MACD Histogram";
          default -> "MACD";
        };
    StringBuilder sb = new StringBuilder();
    sb.append(label).append(":\n");

    int start = outputStart(signalStart, filtered.size(), lookBackDays);
    for (int i = start; i < filtered.size(); i++) {
      double value =
          switch (component) {
            case "macds" -> signal[i];
            case "macdh" -> macdLine[i] - signal[i];
            default -> macdLine[i];
          };
      appendLine(sb, filtered.get(i).date(), value);
    }
    return sb.toString();
  }
}
