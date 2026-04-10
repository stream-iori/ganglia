package work.ganglia.trading.data.vendor.yfinance;

import java.time.LocalDate;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.vendor.DataVendorSpi;

/** Yahoo Finance news vendor. Fetches ticker-specific and global news via the search API. */
public class YFinanceNewsVendor implements DataVendorSpi.News {

  private static final String[] GLOBAL_QUERIES = {
    "stock market economy", "Federal Reserve interest rates", "global markets today"
  };

  private final YFinanceClient client;

  public YFinanceNewsVendor(YFinanceClient client) {
    this.client = client;
  }

  @Override
  public Future<String> getNews(String ticker, String startDate, String endDate) {
    String uri = "/v1/finance/search?q=" + ticker + "&newsCount=20&enableFuzzyQuery=false";
    return client
        .get(YFinanceClient.SEARCH_HOST, uri)
        .map(json -> formatNews(json, ticker, startDate, endDate));
  }

  @Override
  public Future<String> getGlobalNews(String currDate, int lookBackDays, int limit) {
    String startDate = LocalDate.parse(currDate).minusDays(lookBackDays).toString();

    // Fetch news from multiple macro queries and merge
    @SuppressWarnings("unchecked")
    Future<String>[] futures = new Future[GLOBAL_QUERIES.length];
    for (int i = 0; i < GLOBAL_QUERIES.length; i++) {
      String query = GLOBAL_QUERIES[i];
      String uri =
          "/v1/finance/search?q="
              + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8)
              + "&newsCount="
              + Math.min(limit, 10)
              + "&enableFuzzyQuery=true";
      futures[i] = client.get(YFinanceClient.SEARCH_HOST, uri);
    }

    return Future.all(java.util.Arrays.asList(futures))
        .map(
            composite -> {
              StringBuilder sb = new StringBuilder();
              sb.append("=== Global Market News ===\n\n");
              int count = 0;
              for (int i = 0; i < GLOBAL_QUERIES.length && count < limit; i++) {
                String json = composite.resultAt(i);
                String articles = formatNews(json, "Global", startDate, currDate);
                if (!articles.contains("No news")) {
                  sb.append(articles).append('\n');
                  count += countArticles(articles);
                }
              }
              return sb.length() > 30 ? sb.toString() : "No global news found for the period.";
            });
  }

  static String formatNews(String json, String context, String startDate, String endDate) {
    JsonObject root = new JsonObject(json);
    JsonArray news = root.getJsonArray("news");
    if (news == null || news.isEmpty()) return "No news found for " + context;

    StringBuilder sb = new StringBuilder();
    sb.append("=== News for ").append(context).append(" ===\n\n");
    int count = 0;

    for (int i = 0; i < news.size(); i++) {
      JsonObject article = news.getJsonObject(i);
      String title = article.getString("title", "");
      String publisher = article.getString("publisher", "");
      String link = article.getString("link", "");
      long publishTime = article.getLong("providerPublishTime", 0L);

      // Convert epoch to date string
      String pubDate = publishTime > 0 ? LocalDate.ofEpochDay(publishTime / 86400).toString() : "";

      // Date range filter
      if (!pubDate.isEmpty()) {
        if (startDate != null && pubDate.compareTo(startDate) < 0) continue;
        if (endDate != null && pubDate.compareTo(endDate) > 0) continue;
      }

      count++;
      sb.append(count).append(". ").append(title).append('\n');
      if (!pubDate.isEmpty()) sb.append("   Date: ").append(pubDate).append('\n');
      if (!publisher.isEmpty()) sb.append("   Source: ").append(publisher).append('\n');
      if (!link.isEmpty()) sb.append("   Link: ").append(link).append('\n');
      sb.append('\n');
    }

    if (count == 0) return "No news found for " + context + " in the specified date range.";
    return sb.toString();
  }

  private static int countArticles(String formatted) {
    return (int) formatted.lines().filter(l -> l.matches("^\\d+\\..*")).count();
  }
}
