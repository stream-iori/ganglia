package work.ganglia.trading.data.vendor.alphavantage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AlphaVantageNewsVendorTest {

  private static final String NEWS_JSON =
      """
      {
        "items": "3",
        "sentiment_score_definition": "...",
        "feed": [
          {
            "title": "Apple Reports Record Revenue",
            "url": "https://example.com/apple-revenue",
            "time_published": "20240131T1530",
            "source": "Reuters",
            "summary": "Apple Inc reported record quarterly revenue...",
            "overall_sentiment_score": 0.85
          },
          {
            "title": "Tech Sector Outlook 2024",
            "url": "https://example.com/tech-outlook",
            "time_published": "20240130T0900",
            "source": "Bloomberg",
            "summary": "The technology sector is expected to...",
            "overall_sentiment_score": 0.42
          }
        ]
      }
      """;

  @Test
  void formatNewsBasic() {
    String result = AlphaVantageNewsVendor.formatNews(NEWS_JSON, "AAPL");
    assertTrue(result.contains("=== News for AAPL ==="));
    assertTrue(result.contains("Apple Reports Record Revenue"));
    assertTrue(result.contains("Reuters"));
    assertTrue(result.contains("2024-01-31 15:30"));
    assertTrue(result.contains("Sentiment: 0.85"));
    assertTrue(result.contains("Tech Sector Outlook 2024"));
  }

  @Test
  void formatNewsEmpty() {
    String json =
        """
        {"feed": []}
        """;
    String result = AlphaVantageNewsVendor.formatNews(json, "AAPL");
    assertTrue(result.contains("No news found"));
  }

  @Test
  void formatNewsNoFeed() {
    String json =
        """
        {"items": "0"}
        """;
    String result = AlphaVantageNewsVendor.formatNews(json, "AAPL");
    assertTrue(result.contains("No news found"));
  }

  @Test
  void rateLimitDetection() {
    assertTrue(
        AlphaVantageClient.isRateLimited(
            "{\"Information\": \"Thank you for using Alpha Vantage! API call frequency is 5 calls per minute.\"}"));
    assertTrue(
        AlphaVantageClient.isRateLimited("{\"Note\": \"Thank you for using Alpha Vantage!\"}"));
    assertFalse(AlphaVantageClient.isRateLimited("{\"feed\": []}"));
    assertFalse(AlphaVantageClient.isRateLimited(null));
  }
}
