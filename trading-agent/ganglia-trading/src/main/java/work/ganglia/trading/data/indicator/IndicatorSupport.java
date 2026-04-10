package work.ganglia.trading.data.indicator;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Shared utility methods for technical indicator implementations. */
public final class IndicatorSupport {
  private IndicatorSupport() {}

  /** Filter OHLCV rows to only include dates on or before currDate. */
  public static List<OhlcvRow> filterByDate(List<OhlcvRow> data, String currDate) {
    if (currDate == null || currDate.isBlank()) return data;
    return data.stream().filter(r -> r.date().compareTo(currDate) <= 0).toList();
  }

  /** Format a double value to 2 decimal places. */
  public static String format(double value) {
    return String.format("%.2f", value);
  }

  /** Build an "insufficient data" error message. */
  public static String insufficientData(int need, int have) {
    return "Insufficient data: need " + need + " rows, have " + have;
  }

  /** Append a formatted date-value line to the StringBuilder. */
  public static void appendLine(StringBuilder sb, String date, double value) {
    sb.append(date).append(": ").append(format(value)).append('\n');
  }

  /**
   * Compute EMA for an entire array. Returns array of same length; values before seed index are 0.
   */
  public static double[] computeEmaArray(double[] values, int period) {
    double[] result = new double[values.length];
    if (values.length < period) return result;

    double ema = 0;
    for (int i = 0; i < period; i++) {
      ema += values[i];
    }
    ema /= period;
    result[period - 1] = ema;

    double multiplier = 2.0 / (period + 1);
    for (int i = period; i < values.length; i++) {
      ema = (values[i] - ema) * multiplier + ema;
      result[i] = ema;
    }
    return result;
  }

  /** Compute the start index for lookBackDays output within a filtered dataset. */
  public static int outputStart(int minIndex, int dataSize, int lookBackDays) {
    return Math.max(minIndex, dataSize - lookBackDays);
  }
}
