package work.ganglia.trading.data.vendor.yfinance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class YFinanceFundamentalsVendorTest {

  private static final String FUNDAMENTALS_JSON =
      """
      {
        "quoteSummary": {
          "result": [{
            "assetProfile": {
              "sector": "Technology",
              "industry": "Consumer Electronics",
              "fullTimeEmployees": 164000,
              "website": "https://www.apple.com"
            },
            "financialData": {
              "currentPrice": {"raw": 195.89, "fmt": "195.89"},
              "totalRevenue": {"raw": 383285000000, "fmt": "383.29B"},
              "profitMargins": {"raw": 0.2531, "fmt": "25.31%"},
              "returnOnEquity": {"raw": 1.7195, "fmt": "171.95%"},
              "freeCashflow": {"raw": 111443000000, "fmt": "111.44B"}
            },
            "defaultKeyStatistics": {
              "trailingEps": {"raw": 6.42, "fmt": "6.42"},
              "forwardPE": {"raw": 28.5, "fmt": "28.5"},
              "pegRatio": {"raw": 2.11, "fmt": "2.11"},
              "beta": {"raw": 1.29, "fmt": "1.29"},
              "enterpriseValue": {"raw": 3050000000000, "fmt": "3.05T"}
            }
          }]
        }
      }
      """;

  @Test
  void formatFundamentalsBasic() {
    String result = YFinanceFundamentalsVendor.formatFundamentals(FUNDAMENTALS_JSON, "AAPL");
    assertTrue(result.contains("=== Fundamentals for AAPL ==="));
    assertTrue(result.contains("Sector: Technology"));
    assertTrue(result.contains("Industry: Consumer Electronics"));
    assertTrue(result.contains("Current Price: 195.89"));
    assertTrue(result.contains("Profit Margins: 25.31%"));
    assertTrue(result.contains("Return on Equity: 171.95%"));
    assertTrue(result.contains("PEG Ratio: 2.11"));
  }

  @Test
  void formatFundamentalsEmpty() {
    String json =
        """
        {"quoteSummary": {"result": []}}
        """;
    String result = YFinanceFundamentalsVendor.formatFundamentals(json, "AAPL");
    assertTrue(result.contains("No fundamentals data"));
  }

  private static final String BALANCE_SHEET_JSON =
      """
      {
        "quoteSummary": {
          "result": [{
            "balanceSheetHistory": {
              "balanceSheetStatements": [
                {
                  "endDate": {"raw": 1696032000, "fmt": "2023-09-30"},
                  "maxAge": 1,
                  "totalAssets": {"raw": 352583000000, "fmt": "352.58B"},
                  "totalLiab": {"raw": 290437000000, "fmt": "290.44B"}
                },
                {
                  "endDate": {"raw": 1727654400, "fmt": "2024-09-30"},
                  "maxAge": 1,
                  "totalAssets": {"raw": 364980000000, "fmt": "364.98B"},
                  "totalLiab": {"raw": 308030000000, "fmt": "308.03B"}
                }
              ]
            }
          }]
        }
      }
      """;

  @Test
  void formatBalanceSheetWithDateFilter() {
    String result =
        YFinanceFundamentalsVendor.formatFinancialStatement(
            BALANCE_SHEET_JSON, "balanceSheetHistory", "AAPL", "Balance Sheet", "2024-01-01");
    assertTrue(result.contains("2023-09-30"));
    assertFalse(result.contains("2024-09-30")); // filtered out by look-ahead guard
    assertTrue(result.contains("totalAssets: 352.58B"));
  }

  @Test
  void formatBalanceSheetNoFilter() {
    String result =
        YFinanceFundamentalsVendor.formatFinancialStatement(
            BALANCE_SHEET_JSON, "balanceSheetHistory", "AAPL", "Balance Sheet", null);
    assertTrue(result.contains("2023-09-30"));
    assertTrue(result.contains("2024-09-30")); // no filter
  }
}
