package work.ganglia.trading.data.vendor.yfinance;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.cache.OhlcvCache;
import work.ganglia.trading.data.vendor.DataVendorSpi;

/**
 * Yahoo Finance stock OHLCV data vendor. Fetches daily OHLCV via the chart API and caches results.
 */
public class YFinanceStockVendor implements DataVendorSpi.Stock {
  private static final Logger logger = LoggerFactory.getLogger(YFinanceStockVendor.class);

  private final YFinanceClient client;
  private final OhlcvCache cache;

  public YFinanceStockVendor(YFinanceClient client, OhlcvCache cache) {
    this.client = client;
    this.cache = cache;
  }

  @Override
  public Future<String> getStockData(String symbol, String startDate, String endDate) {
    // Check cache first
    return cache
        .get(symbol, endDate)
        .compose(
            cached -> {
              if (cached != null) {
                logger.debug("OHLCV cache hit for {}", symbol);
                return Future.succeededFuture(cached);
              }
              return fetchAndCache(symbol, startDate, endDate);
            });
  }

  private Future<String> fetchAndCache(String symbol, String startDate, String endDate) {
    long period1 = LocalDate.parse(startDate).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    long period2 = LocalDate.parse(endDate).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

    String uri =
        "/v8/finance/chart/"
            + symbol
            + "?period1="
            + period1
            + "&period2="
            + period2
            + "&interval=1d";

    return client
        .get(YFinanceClient.CHART_HOST, uri)
        .map(YFinanceStockVendor::parseChartResponse)
        .compose(
            csv -> {
              if (csv.isEmpty()) {
                return Future.failedFuture(
                    new RuntimeException("No OHLCV data returned for " + symbol));
              }
              return cache.put(symbol, csv).map(csv);
            });
  }

  /** Parse Yahoo Finance chart JSON response into CSV format. */
  static String parseChartResponse(String json) {
    JsonObject root = new JsonObject(json);
    JsonObject chart = root.getJsonObject("chart");
    if (chart == null) return "";

    JsonArray results = chart.getJsonArray("result");
    if (results == null || results.isEmpty()) return "";

    JsonObject result = results.getJsonObject(0);
    JsonArray timestamps = result.getJsonArray("timestamp");
    if (timestamps == null || timestamps.isEmpty()) return "";

    JsonObject indicators = result.getJsonObject("indicators");
    JsonObject quote = indicators.getJsonArray("quote").getJsonObject(0);
    JsonArray opens = quote.getJsonArray("open");
    JsonArray highs = quote.getJsonArray("high");
    JsonArray lows = quote.getJsonArray("low");
    JsonArray closes = quote.getJsonArray("close");
    JsonArray volumes = quote.getJsonArray("volume");

    // Use adjclose if available
    JsonArray adjCloses = null;
    JsonArray adjCloseArr = indicators.getJsonArray("adjclose");
    if (adjCloseArr != null && !adjCloseArr.isEmpty()) {
      adjCloses = adjCloseArr.getJsonObject(0).getJsonArray("adjclose");
    }

    StringBuilder csv = new StringBuilder();
    csv.append("Date,Open,High,Low,Close,Volume\n");

    for (int i = 0; i < timestamps.size(); i++) {
      long epoch = timestamps.getLong(i);
      String date = LocalDate.ofEpochDay(epoch / 86400).toString();

      Double open = getDouble(opens, i);
      Double high = getDouble(highs, i);
      Double low = getDouble(lows, i);
      Double close = adjCloses != null ? getDouble(adjCloses, i) : getDouble(closes, i);
      Long volume = getLong(volumes, i);

      if (open == null || high == null || low == null || close == null) continue;

      csv.append(date)
          .append(',')
          .append(String.format("%.2f", open))
          .append(',')
          .append(String.format("%.2f", high))
          .append(',')
          .append(String.format("%.2f", low))
          .append(',')
          .append(String.format("%.2f", close))
          .append(',')
          .append(volume != null ? volume : 0)
          .append('\n');
    }
    return csv.toString();
  }

  private static Double getDouble(JsonArray arr, int idx) {
    if (arr == null || idx >= arr.size()) return null;
    Object val = arr.getValue(idx);
    if (val == null) return null;
    return ((Number) val).doubleValue();
  }

  private static Long getLong(JsonArray arr, int idx) {
    if (arr == null || idx >= arr.size()) return null;
    Object val = arr.getValue(idx);
    if (val == null) return null;
    return ((Number) val).longValue();
  }
}
