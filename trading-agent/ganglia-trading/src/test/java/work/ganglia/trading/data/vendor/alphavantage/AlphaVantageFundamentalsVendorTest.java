package work.ganglia.trading.data.vendor.alphavantage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AlphaVantageFundamentalsVendorTest {

  private static final String OVERVIEW_JSON =
      """
      {
        "Symbol": "AAPL",
        "Name": "Apple Inc",
        "Sector": "Technology",
        "Industry": "Consumer Electronics",
        "MarketCapitalization": "3000000000000",
        "PERatio": "30.5",
        "EPS": "6.42",
        "Beta": "1.29",
        "52WeekHigh": "199.62",
        "52WeekLow": "164.08",
        "DividendYield": "0.0055",
        "ProfitMargin": "0.2531"
      }
      """;

  @Test
  void formatOverviewBasic() {
    String result = AlphaVantageFundamentalsVendor.formatOverview(OVERVIEW_JSON, "AAPL");
    assertTrue(result.contains("=== Company Overview: AAPL ==="));
    assertTrue(result.contains("Name: Apple Inc"));
    assertTrue(result.contains("Sector: Technology"));
    assertTrue(result.contains("PE Ratio: 30.5"));
    assertTrue(result.contains("Beta: 1.29"));
  }

  @Test
  void formatOverviewEmpty() {
    String result = AlphaVantageFundamentalsVendor.formatOverview("{}", "AAPL");
    assertTrue(result.contains("No fundamentals data"));
  }

  private static final String STATEMENT_JSON =
      """
      {
        "symbol": "AAPL",
        "annualReports": [
          {
            "fiscalDateEnding": "2023-09-30",
            "reportedCurrency": "USD",
            "totalRevenue": "383285000000",
            "netIncome": "96995000000"
          },
          {
            "fiscalDateEnding": "2024-09-30",
            "reportedCurrency": "USD",
            "totalRevenue": "391035000000",
            "netIncome": "100000000000"
          }
        ],
        "quarterlyReports": [
          {
            "fiscalDateEnding": "2024-03-31",
            "reportedCurrency": "USD",
            "totalRevenue": "95000000000",
            "netIncome": "24000000000"
          }
        ]
      }
      """;

  @Test
  void formatStatementAnnual() {
    String result =
        AlphaVantageFundamentalsVendor.formatStatement(
            STATEMENT_JSON, "AAPL", "Income Statement", "annualReports", null);
    assertTrue(result.contains("2023-09-30"));
    assertTrue(result.contains("2024-09-30"));
    assertTrue(result.contains("totalRevenue: 383285000000"));
  }

  @Test
  void formatStatementWithDateFilter() {
    String result =
        AlphaVantageFundamentalsVendor.formatStatement(
            STATEMENT_JSON, "AAPL", "Income Statement", "annualReports", "2024-01-01");
    assertTrue(result.contains("2023-09-30"));
    assertFalse(result.contains("2024-09-30")); // filtered by look-ahead guard
  }

  @Test
  void formatStatementQuarterly() {
    String result =
        AlphaVantageFundamentalsVendor.formatStatement(
            STATEMENT_JSON, "AAPL", "Income Statement", "quarterlyReports", null);
    assertTrue(result.contains("2024-03-31"));
    assertTrue(result.contains("totalRevenue: 95000000000"));
  }
}
