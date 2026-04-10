package work.ganglia.trading.data.vendor.alphavantage;

import java.util.List;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.indicator.IndicatorCalculator;
import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;
import work.ganglia.trading.data.vendor.DataVendorSpi;

/**
 * Alpha Vantage indicator vendor. Uses native API endpoints for SMA, EMA, RSI, MACD, Bollinger
 * Bands, and ATR. Falls back to local computation (via OHLCV + {@link IndicatorCalculator}) for
 * VWMA and MFI which have no native Alpha Vantage endpoint.
 */
public class AlphaVantageIndicatorVendor implements DataVendorSpi.Indicator {

  private final AlphaVantageClient client;
  private final DataVendorSpi.Stock stockVendor;

  public AlphaVantageIndicatorVendor(AlphaVantageClient client, DataVendorSpi.Stock stockVendor) {
    this.client = client;
    this.stockVendor = stockVendor;
  }

  @Override
  public Future<String> getIndicators(
      String symbol, String indicator, String currDate, int lookBackDays) {
    return switch (indicator.toLowerCase()) {
      case "close_50_sma" -> fetchTechIndicator(symbol, "SMA", 50, lookBackDays, currDate);
      case "close_200_sma" -> fetchTechIndicator(symbol, "SMA", 200, lookBackDays, currDate);
      case "close_10_ema" -> fetchTechIndicator(symbol, "EMA", 10, lookBackDays, currDate);
      case "rsi" -> fetchTechIndicator(symbol, "RSI", 14, lookBackDays, currDate);
      case "macd", "macds", "macdh" -> fetchMacd(symbol, indicator, lookBackDays, currDate);
      case "boll", "boll_ub", "boll_lb" -> fetchBbands(symbol, indicator, lookBackDays, currDate);
      case "atr" -> fetchAtr(symbol, lookBackDays, currDate);
      // VWMA and MFI have no native Alpha Vantage endpoint — compute locally from OHLCV
      case "vwma", "mfi" -> computeLocally(symbol, indicator, lookBackDays, currDate);
      default ->
          Future.succeededFuture(
              "Unsupported indicator: "
                  + indicator
                  + ". Supported: close_50_sma, close_200_sma, close_10_ema, rsi, "
                  + "macd, macds, macdh, boll, boll_ub, boll_lb, atr, vwma, mfi");
    };
  }

  private Future<String> fetchMacd(
      String symbol, String component, int lookBackDays, String currDate) {
    String params = "symbol=" + symbol + "&interval=daily&series_type=close";
    return client
        .query("MACD", params)
        .map(json -> formatMacd(json, component, lookBackDays, currDate));
  }

  private Future<String> fetchBbands(
      String symbol, String component, int lookBackDays, String currDate) {
    String params =
        "symbol=" + symbol + "&interval=daily&time_period=20&series_type=close&nbdevup=2&nbdevdn=2";
    return client
        .query("BBANDS", params)
        .map(json -> formatBbands(json, component, lookBackDays, currDate));
  }

  private Future<String> fetchAtr(String symbol, int lookBackDays, String currDate) {
    String params = "symbol=" + symbol + "&interval=daily&time_period=14";
    return client
        .query("ATR", params)
        .map(json -> formatIndicator(json, "ATR(14)", lookBackDays, currDate));
  }

  private Future<String> computeLocally(
      String symbol, String indicator, int lookBackDays, String currDate) {
    String startDate;
    try {
      startDate = java.time.LocalDate.parse(currDate).minusYears(5).toString();
    } catch (Exception e) {
      startDate = "2019-01-01";
    }
    return stockVendor
        .getStockData(symbol, startDate, currDate)
        .map(
            csv -> {
              List<OhlcvRow> data = IndicatorCalculator.parseCsv(csv);
              return IndicatorCalculator.compute(data, indicator, lookBackDays, currDate);
            });
  }

  static String formatMacd(String json, String component, int lookBackDays, String currDate) {
    JsonObject root = new JsonObject(json);
    String metaKey =
        root.fieldNames().stream()
            .filter(k -> k.startsWith("Technical Analysis"))
            .findFirst()
            .orElse(null);
    if (metaKey == null) return "No MACD data available";

    JsonObject data = root.getJsonObject(metaKey);
    if (data == null || data.isEmpty()) return "No MACD data available";

    String valueKey =
        switch (component) {
          case "macds" -> "MACD_Signal";
          case "macdh" -> "MACD_Hist";
          default -> "MACD";
        };
    String label =
        switch (component) {
          case "macds" -> "MACD Signal";
          case "macdh" -> "MACD Histogram";
          default -> "MACD";
        };

    StringBuilder sb = new StringBuilder();
    sb.append(label).append(":\n");
    java.util.List<String> filtered =
        data.fieldNames().stream()
            .sorted()
            .filter(date -> currDate == null || date.compareTo(currDate) <= 0)
            .toList();
    int fromIndex = Math.max(0, filtered.size() - lookBackDays);
    filtered
        .subList(fromIndex, filtered.size())
        .forEach(
            date -> {
              JsonObject values = data.getJsonObject(date);
              String value = values.getString(valueKey, "N/A");
              sb.append(date).append(": ").append(value).append('\n');
            });
    return sb.toString();
  }

  static String formatBbands(String json, String component, int lookBackDays, String currDate) {
    JsonObject root = new JsonObject(json);
    String metaKey =
        root.fieldNames().stream()
            .filter(k -> k.startsWith("Technical Analysis"))
            .findFirst()
            .orElse(null);
    if (metaKey == null) return "No Bollinger Bands data available";

    JsonObject data = root.getJsonObject(metaKey);
    if (data == null || data.isEmpty()) return "No Bollinger Bands data available";

    String valueKey =
        switch (component) {
          case "boll_ub" -> "Real Upper Band";
          case "boll_lb" -> "Real Lower Band";
          default -> "Real Middle Band";
        };
    String label =
        switch (component) {
          case "boll_ub" -> "Bollinger Upper Band";
          case "boll_lb" -> "Bollinger Lower Band";
          default -> "Bollinger Middle Band";
        };

    StringBuilder sb = new StringBuilder();
    sb.append(label).append(":\n");
    java.util.List<String> filtered =
        data.fieldNames().stream()
            .sorted()
            .filter(date -> currDate == null || date.compareTo(currDate) <= 0)
            .toList();
    int fromIndex = Math.max(0, filtered.size() - lookBackDays);
    filtered
        .subList(fromIndex, filtered.size())
        .forEach(
            date -> {
              JsonObject values = data.getJsonObject(date);
              String value = values.getString(valueKey, "N/A");
              sb.append(date).append(": ").append(value).append('\n');
            });
    return sb.toString();
  }

  private Future<String> fetchTechIndicator(
      String symbol, String function, int period, int lookBackDays, String currDate) {
    String params =
        "symbol=" + symbol + "&interval=daily&time_period=" + period + "&series_type=close";
    return client
        .query(function, params)
        .map(json -> formatIndicator(json, function + "(" + period + ")", lookBackDays, currDate));
  }

  static String formatIndicator(String json, String label, int lookBackDays, String currDate) {
    JsonObject root = new JsonObject(json);
    // Alpha Vantage uses "Technical Analysis: SMA" etc. as key
    String metaKey =
        root.fieldNames().stream()
            .filter(k -> k.startsWith("Technical Analysis"))
            .findFirst()
            .orElse(null);
    if (metaKey == null) return "No indicator data available for " + label;

    JsonObject data = root.getJsonObject(metaKey);
    if (data == null || data.isEmpty()) return "No indicator data available for " + label;

    StringBuilder sb = new StringBuilder();
    sb.append(label).append(":\n");

    java.util.List<String> filtered =
        data.fieldNames().stream()
            .sorted()
            .filter(date -> currDate == null || date.compareTo(currDate) <= 0)
            .toList();
    // Take last N entries (most recent lookBackDays)
    int fromIndex = Math.max(0, filtered.size() - lookBackDays);
    filtered.subList(fromIndex, filtered.size()).stream()
        .forEach(
            date -> {
              JsonObject values = data.getJsonObject(date);
              String value =
                  values.fieldNames().stream().findFirst().map(values::getString).orElse("N/A");
              sb.append(date).append(": ").append(value).append('\n');
            });
    return sb.toString();
  }
}
