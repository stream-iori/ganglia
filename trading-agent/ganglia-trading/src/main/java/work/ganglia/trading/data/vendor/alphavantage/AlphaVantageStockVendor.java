package work.ganglia.trading.data.vendor.alphavantage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.cache.OhlcvCache;
import work.ganglia.trading.data.vendor.DataVendorSpi;

/** Alpha Vantage stock OHLCV vendor using TIME_SERIES_DAILY_ADJUSTED. */
public class AlphaVantageStockVendor implements DataVendorSpi.Stock {

  private final AlphaVantageClient client;
  private final OhlcvCache cache;

  public AlphaVantageStockVendor(AlphaVantageClient client, OhlcvCache cache) {
    this.client = client;
    this.cache = cache;
  }

  @Override
  public Future<String> getStockData(String symbol, String startDate, String endDate) {
    return cache
        .get(symbol, endDate)
        .compose(
            cached -> {
              if (cached != null) return Future.succeededFuture(cached);
              return fetchAndCache(symbol, startDate, endDate);
            });
  }

  private Future<String> fetchAndCache(String symbol, String startDate, String endDate) {
    return client
        .query("TIME_SERIES_DAILY_ADJUSTED", "symbol=" + symbol + "&outputsize=full")
        .map(json -> parseTimeSeries(json, startDate, endDate))
        .compose(
            csv -> {
              if (csv.isEmpty()) {
                return Future.failedFuture(
                    new RuntimeException("No OHLCV data from Alpha Vantage for " + symbol));
              }
              return cache.put(symbol, csv).map(csv);
            });
  }

  static String parseTimeSeries(String json, String startDate, String endDate) {
    JsonObject root = new JsonObject(json);
    JsonObject timeSeries = root.getJsonObject("Time Series (Daily)");
    if (timeSeries == null) return "";

    StringBuilder csv = new StringBuilder();
    csv.append("Date,Open,High,Low,Close,Volume\n");

    // Sort dates ascending
    timeSeries.fieldNames().stream()
        .sorted()
        .filter(date -> (startDate == null || date.compareTo(startDate) >= 0))
        .filter(date -> (endDate == null || date.compareTo(endDate) <= 0))
        .forEach(
            date -> {
              JsonObject day = timeSeries.getJsonObject(date);
              csv.append(date)
                  .append(',')
                  .append(day.getString("1. open"))
                  .append(',')
                  .append(day.getString("2. high"))
                  .append(',')
                  .append(day.getString("3. low"))
                  .append(',')
                  .append(day.getString("5. adjusted close", day.getString("4. close")))
                  .append(',')
                  .append(day.getString("6. volume", day.getString("5. volume")))
                  .append('\n');
            });
    return csv.toString();
  }
}
