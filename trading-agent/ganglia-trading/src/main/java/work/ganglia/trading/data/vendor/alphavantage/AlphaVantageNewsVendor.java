package work.ganglia.trading.data.vendor.alphavantage;

import java.time.LocalDate;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.vendor.DataVendorSpi;

/** Alpha Vantage news vendor using NEWS_SENTIMENT endpoint. */
public class AlphaVantageNewsVendor implements DataVendorSpi.News {

  private final AlphaVantageClient client;

  public AlphaVantageNewsVendor(AlphaVantageClient client) {
    this.client = client;
  }

  @Override
  public Future<String> getNews(String ticker, String startDate, String endDate) {
    String timeFrom = startDate.replace("-", "") + "T0000";
    String timeTo = endDate.replace("-", "") + "T2359";
    return client
        .query(
            "NEWS_SENTIMENT",
            "tickers=" + ticker + "&time_from=" + timeFrom + "&time_to=" + timeTo + "&limit=20")
        .map(json -> formatNews(json, ticker));
  }

  @Override
  public Future<String> getGlobalNews(String currDate, int lookBackDays, int limit) {
    String startDate = LocalDate.parse(currDate).minusDays(lookBackDays).toString();
    String timeFrom = startDate.replace("-", "") + "T0000";
    String timeTo = currDate.replace("-", "") + "T2359";
    return client
        .query("NEWS_SENTIMENT", "time_from=" + timeFrom + "&time_to=" + timeTo + "&limit=" + limit)
        .map(json -> formatNews(json, "Global Market"));
  }

  static String formatNews(String json, String context) {
    JsonObject root = new JsonObject(json);
    JsonArray feed = root.getJsonArray("feed");
    if (feed == null || feed.isEmpty()) return "No news found for " + context;

    StringBuilder sb = new StringBuilder();
    sb.append("=== News for ").append(context).append(" ===\n\n");

    for (int i = 0; i < feed.size(); i++) {
      JsonObject article = feed.getJsonObject(i);
      String title = article.getString("title", "");
      String source = article.getString("source", "");
      String url = article.getString("url", "");
      String timePublished = article.getString("time_published", "");
      String summary = article.getString("summary", "");
      Double sentiment = article.getDouble("overall_sentiment_score");

      sb.append(i + 1).append(". ").append(title).append('\n');
      if (!timePublished.isEmpty()) {
        sb.append("   Date: ").append(formatTimestamp(timePublished)).append('\n');
      }
      if (!source.isEmpty()) sb.append("   Source: ").append(source).append('\n');
      if (sentiment != null) {
        sb.append("   Sentiment: ").append(String.format("%.2f", sentiment)).append('\n');
      }
      if (!summary.isEmpty()) {
        String truncated = summary.length() > 200 ? summary.substring(0, 200) + "..." : summary;
        sb.append("   Summary: ").append(truncated).append('\n');
      }
      if (!url.isEmpty()) sb.append("   Link: ").append(url).append('\n');
      sb.append('\n');
    }
    return sb.toString();
  }

  /** Convert "20240131T1530" format to "2024-01-31 15:30". */
  private static String formatTimestamp(String ts) {
    if (ts.length() < 13) return ts;
    return ts.substring(0, 4)
        + "-"
        + ts.substring(4, 6)
        + "-"
        + ts.substring(6, 8)
        + " "
        + ts.substring(9, 11)
        + ":"
        + ts.substring(11, 13);
  }
}
