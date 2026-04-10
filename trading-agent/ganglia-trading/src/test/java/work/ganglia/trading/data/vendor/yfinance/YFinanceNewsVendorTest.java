package work.ganglia.trading.data.vendor.yfinance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class YFinanceNewsVendorTest {

  private static final String NEWS_JSON =
      """
      {
        "news": [
          {
            "title": "Apple Q4 Earnings Beat Estimates",
            "publisher": "Reuters",
            "link": "https://example.com/apple-earnings",
            "providerPublishTime": 1706659200
          },
          {
            "title": "Tech Stocks Rally",
            "publisher": "Bloomberg",
            "link": "https://example.com/tech-rally",
            "providerPublishTime": 1704153600
          },
          {
            "title": "Old News Article",
            "publisher": "WSJ",
            "link": "https://example.com/old",
            "providerPublishTime": 1672531200
          }
        ]
      }
      """;

  @Test
  void formatNewsBasic() {
    String result = YFinanceNewsVendor.formatNews(NEWS_JSON, "AAPL", null, null);
    assertTrue(result.contains("=== News for AAPL ==="));
    assertTrue(result.contains("Apple Q4 Earnings Beat Estimates"));
    assertTrue(result.contains("Reuters"));
    assertTrue(result.contains("Tech Stocks Rally"));
    assertTrue(result.contains("Bloomberg"));
  }

  @Test
  void formatNewsDateRangeFilter() {
    // Only include articles from Jan 2024 to Feb 2024
    String result = YFinanceNewsVendor.formatNews(NEWS_JSON, "AAPL", "2024-01-01", "2024-02-28");
    assertTrue(result.contains("Apple Q4 Earnings Beat Estimates"));
    assertTrue(result.contains("Tech Stocks Rally"));
    assertFalse(result.contains("Old News Article")); // 2023 article filtered out
  }

  @Test
  void formatNewsEmptyResult() {
    String json =
        """
        {"news": []}
        """;
    String result = YFinanceNewsVendor.formatNews(json, "AAPL", null, null);
    assertTrue(result.contains("No news found"));
  }

  @Test
  void formatNewsAllFilteredOut() {
    // Narrow date range that excludes all articles
    String result = YFinanceNewsVendor.formatNews(NEWS_JSON, "AAPL", "2025-01-01", "2025-12-31");
    assertTrue(result.contains("No news found"));
  }
}
