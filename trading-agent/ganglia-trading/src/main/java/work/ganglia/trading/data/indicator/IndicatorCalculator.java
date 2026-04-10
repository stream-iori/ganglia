package work.ganglia.trading.data.indicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Facade for technical indicator computation. Retains the original static API for backward
 * compatibility while delegating to {@link IndicatorRegistry} and individual {@link
 * TechnicalIndicator} implementations.
 */
public final class IndicatorCalculator {
  private IndicatorCalculator() {}

  private static final IndicatorRegistry REGISTRY = IndicatorRegistry.createDefault();

  /** A single OHLCV row. */
  public record OhlcvRow(
      String date, double open, double high, double low, double close, long volume) {}

  /** Parse CSV string (with header) into OhlcvRow list. */
  public static List<OhlcvRow> parseCsv(String csv) {
    List<OhlcvRow> rows = new ArrayList<>();
    String[] lines = csv.split("\n");
    for (int i = 1; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) continue;
      String[] cols = line.split(",");
      if (cols.length < 6) continue;
      try {
        rows.add(
            new OhlcvRow(
                cols[0].trim(),
                Double.parseDouble(cols[1].trim()),
                Double.parseDouble(cols[2].trim()),
                Double.parseDouble(cols[3].trim()),
                Double.parseDouble(cols[4].trim()),
                Long.parseLong(cols[5].trim())));
      } catch (NumberFormatException e) {
        // Skip malformed rows
      }
    }
    return rows;
  }

  /** Compute SMA. */
  public static String sma(List<OhlcvRow> data, int period, int lookBackDays, String currDate) {
    String name = period == 200 ? "close_200_sma" : "close_50_sma";
    return new SmaIndicator(period, name).compute(data, lookBackDays, currDate);
  }

  /** Compute EMA. */
  public static String ema(List<OhlcvRow> data, int period, int lookBackDays, String currDate) {
    return new EmaIndicator(period, "close_" + period + "_ema")
        .compute(data, lookBackDays, currDate);
  }

  /** Compute RSI. */
  public static String rsi(List<OhlcvRow> data, int period, int lookBackDays, String currDate) {
    return new RsiIndicator().compute(data, lookBackDays, currDate);
  }

  /** Compute MACD. */
  public static String macd(
      List<OhlcvRow> data, String component, int lookBackDays, String currDate) {
    return new MacdIndicator(component).compute(data, lookBackDays, currDate);
  }

  /** Compute Bollinger Bands. */
  public static String bollingerBands(
      List<OhlcvRow> data, String component, int lookBackDays, String currDate) {
    return new BollingerBandsIndicator(component).compute(data, lookBackDays, currDate);
  }

  /** Compute ATR. */
  public static String atr(List<OhlcvRow> data, int lookBackDays, String currDate) {
    return new AtrIndicator().compute(data, lookBackDays, currDate);
  }

  /** Compute VWMA. */
  public static String vwma(List<OhlcvRow> data, int lookBackDays, String currDate) {
    return new VwmaIndicator().compute(data, lookBackDays, currDate);
  }

  /** Compute MFI. */
  public static String mfi(List<OhlcvRow> data, int lookBackDays, String currDate) {
    return new MfiIndicator().compute(data, lookBackDays, currDate);
  }

  /** Dispatch indicator computation by name. */
  public static String compute(
      List<OhlcvRow> data, String indicator, int lookBackDays, String currDate) {
    return REGISTRY.compute(data, indicator, lookBackDays, currDate);
  }
}
