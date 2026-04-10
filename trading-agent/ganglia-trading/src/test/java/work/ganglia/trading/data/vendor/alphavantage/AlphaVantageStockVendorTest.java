package work.ganglia.trading.data.vendor.alphavantage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AlphaVantageStockVendorTest {

  private static final String TIME_SERIES_JSON =
      """
      {
        "Meta Data": {
          "1. Information": "Daily Time Series with Splits and Dividend Events",
          "2. Symbol": "AAPL"
        },
        "Time Series (Daily)": {
          "2024-01-02": {
            "1. open": "100.00",
            "2. high": "105.00",
            "3. low": "99.00",
            "4. close": "104.00",
            "5. adjusted close": "103.50",
            "6. volume": "1000000"
          },
          "2024-01-03": {
            "1. open": "104.00",
            "2. high": "106.00",
            "3. low": "103.00",
            "4. close": "105.00",
            "5. adjusted close": "104.50",
            "6. volume": "1100000"
          },
          "2024-01-04": {
            "1. open": "105.00",
            "2. high": "107.00",
            "3. low": "104.00",
            "4. close": "106.00",
            "5. adjusted close": "105.50",
            "6. volume": "900000"
          }
        }
      }
      """;

  @Test
  void parseTimeSeriesBasic() {
    String csv = AlphaVantageStockVendor.parseTimeSeries(TIME_SERIES_JSON, null, null);
    assertTrue(csv.startsWith("Date,Open,High,Low,Close,Volume\n"));
    assertTrue(csv.contains("2024-01-02,100.00,105.00,99.00,103.50,1000000"));
    assertTrue(csv.contains("2024-01-03"));
    assertTrue(csv.contains("2024-01-04"));
    // Sorted ascending
    int idx2 = csv.indexOf("2024-01-02");
    int idx3 = csv.indexOf("2024-01-03");
    assertTrue(idx2 < idx3);
  }

  @Test
  void parseTimeSeriesDateFilter() {
    String csv =
        AlphaVantageStockVendor.parseTimeSeries(TIME_SERIES_JSON, "2024-01-02", "2024-01-03");
    assertTrue(csv.contains("2024-01-02"));
    assertTrue(csv.contains("2024-01-03"));
    assertFalse(csv.contains("2024-01-04"));
  }

  @Test
  void parseTimeSeriesEmpty() {
    String json =
        """
        {"Meta Data": {}}
        """;
    String csv = AlphaVantageStockVendor.parseTimeSeries(json, null, null);
    assertEquals("", csv);
  }
}
