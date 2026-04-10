package work.ganglia.trading.data.vendor.yfinance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class YFinanceStockVendorTest {

  private static final String CHART_JSON =
      """
      {
        "chart": {
          "result": [{
            "timestamp": [1704153600, 1704240000, 1704326400],
            "indicators": {
              "quote": [{
                "open": [100.0, 101.0, 102.0],
                "high": [105.0, 106.0, 107.0],
                "low": [99.0, 100.0, 101.0],
                "close": [104.0, 105.0, 106.0],
                "volume": [1000000, 1100000, 900000]
              }],
              "adjclose": [{
                "adjclose": [103.5, 104.5, 105.5]
              }]
            }
          }]
        }
      }
      """;

  @Test
  void parseChartResponseBasic() {
    String csv = YFinanceStockVendor.parseChartResponse(CHART_JSON);
    assertTrue(csv.startsWith("Date,Open,High,Low,Close,Volume\n"));
    // Should use adjclose values
    assertTrue(csv.contains("103.50"));
    assertTrue(csv.contains("104.50"));
    assertTrue(csv.contains("105.50"));
    // Should have 3 data rows + header
    assertEquals(4, csv.split("\n").length);
  }

  @Test
  void parseChartResponseNoAdjClose() {
    String json =
        """
        {
          "chart": {
            "result": [{
              "timestamp": [1704153600],
              "indicators": {
                "quote": [{
                  "open": [100.0],
                  "high": [105.0],
                  "low": [99.0],
                  "close": [104.0],
                  "volume": [1000000]
                }]
              }
            }]
          }
        }
        """;
    String csv = YFinanceStockVendor.parseChartResponse(json);
    assertTrue(csv.contains("104.00")); // falls back to close
  }

  @Test
  void parseChartResponseEmpty() {
    String json =
        """
        {"chart": {"result": []}}
        """;
    String csv = YFinanceStockVendor.parseChartResponse(json);
    assertEquals("", csv);
  }

  @Test
  void parseChartResponseNullValues() {
    String json =
        """
        {
          "chart": {
            "result": [{
              "timestamp": [1704153600, 1704240000],
              "indicators": {
                "quote": [{
                  "open": [100.0, null],
                  "high": [105.0, null],
                  "low": [99.0, 100.0],
                  "close": [104.0, null],
                  "volume": [1000000, 1100000]
                }]
              }
            }]
          }
        }
        """;
    String csv = YFinanceStockVendor.parseChartResponse(json);
    // Null row should be skipped
    assertEquals(2, csv.split("\n").length); // header + 1 data row
  }
}
