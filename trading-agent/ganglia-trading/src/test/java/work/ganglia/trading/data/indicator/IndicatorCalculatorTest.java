package work.ganglia.trading.data.indicator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

class IndicatorCalculatorTest {

  /** Build synthetic close prices = 10, 11, 12, ..., 10+n-1 */
  private static List<OhlcvRow> syntheticData(int n) {
    return java.util.stream.IntStream.range(0, n)
        .mapToObj(
            i -> {
              String date = String.format("2024-01-%02d", i + 1);
              double close = 10.0 + i;
              return new OhlcvRow(date, close, close + 1, close - 1, close, 1000L * (i + 1));
            })
        .toList();
  }

  @Test
  void parseCsvBasic() {
    String csv = "Date,Open,High,Low,Close,Volume\n2024-01-01,10.0,11.0,9.0,10.5,1000\n";
    List<OhlcvRow> rows = IndicatorCalculator.parseCsv(csv);
    assertEquals(1, rows.size());
    assertEquals("2024-01-01", rows.get(0).date());
    assertEquals(10.5, rows.get(0).close());
  }

  @Test
  void parseCsvSkipsMalformed() {
    String csv = "Date,Open,High,Low,Close,Volume\nbad,data\n2024-01-01,10,11,9,10.5,1000\n";
    List<OhlcvRow> rows = IndicatorCalculator.parseCsv(csv);
    assertEquals(1, rows.size());
  }

  @Test
  void smaBasic() {
    // 5 data points, SMA(3) on last 2 days
    List<OhlcvRow> data = syntheticData(5); // closes: 10,11,12,13,14
    String result = IndicatorCalculator.sma(data, 3, 2, "2024-01-05");
    assertTrue(result.contains("SMA(3)"));
    // SMA(3) at day 4 = (11+12+13)/3 = 12.00
    assertTrue(result.contains("12.00"));
    // SMA(3) at day 5 = (12+13+14)/3 = 13.00
    assertTrue(result.contains("13.00"));
  }

  @Test
  void smaInsufficientData() {
    List<OhlcvRow> data = syntheticData(2);
    String result = IndicatorCalculator.sma(data, 50, 30, "2024-01-02");
    assertTrue(result.contains("Insufficient"));
  }

  @Test
  void emaBasic() {
    List<OhlcvRow> data = syntheticData(15); // closes: 10..24
    String result = IndicatorCalculator.ema(data, 10, 3, "2024-01-15");
    assertTrue(result.contains("EMA(10)"));
    // Should have 3 date entries
    long lineCount = result.lines().filter(l -> l.contains("2024-01-")).count();
    assertEquals(3, lineCount);
  }

  @Test
  void rsiBasic() {
    // All rising prices → RSI should be 100
    List<OhlcvRow> data = syntheticData(20); // closes: 10..29
    String result = IndicatorCalculator.rsi(data, 14, 3, "2024-01-20");
    assertTrue(result.contains("RSI(14)"));
    assertTrue(result.contains("100.00")); // all gains, no losses
  }

  @Test
  void rsiInsufficientData() {
    List<OhlcvRow> data = syntheticData(5);
    String result = IndicatorCalculator.rsi(data, 14, 30, "2024-01-05");
    assertTrue(result.contains("Insufficient"));
  }

  @Test
  void computeDispatch() {
    List<OhlcvRow> data = syntheticData(60);
    assertTrue(
        IndicatorCalculator.compute(data, "close_50_sma", 5, "2024-02-29").contains("SMA(50)"));
    assertTrue(
        IndicatorCalculator.compute(data, "close_10_ema", 5, "2024-02-29").contains("EMA(10)"));
    assertTrue(IndicatorCalculator.compute(data, "rsi", 5, "2024-02-29").contains("RSI(14)"));
  }

  @Test
  void macdBasic() {
    // Need at least 35 rows (26 for slow EMA + 9 for signal seed)
    List<OhlcvRow> data = syntheticData(50); // closes: 10..59
    String macdResult = IndicatorCalculator.compute(data, "macd", 3, "2024-02-19");
    assertTrue(macdResult.contains("MACD"));
    // With strictly rising prices, EMA(12) > EMA(26), so MACD should be positive
    assertFalse(macdResult.contains("Insufficient"));

    String signalResult = IndicatorCalculator.compute(data, "macds", 3, "2024-02-19");
    assertTrue(signalResult.contains("MACD Signal"));

    String histResult = IndicatorCalculator.compute(data, "macdh", 3, "2024-02-19");
    assertTrue(histResult.contains("MACD Histogram"));
  }

  @Test
  void macdInsufficientData() {
    List<OhlcvRow> data = syntheticData(20);
    String result = IndicatorCalculator.compute(data, "macd", 5, "2024-01-20");
    assertTrue(result.contains("Insufficient"));
  }

  @Test
  void bollingerBandsBasic() {
    List<OhlcvRow> data = syntheticData(30); // closes: 10..39
    String middle = IndicatorCalculator.compute(data, "boll", 3, "2024-01-30");
    assertTrue(middle.contains("Bollinger Middle Band"));

    String upper = IndicatorCalculator.compute(data, "boll_ub", 3, "2024-01-30");
    assertTrue(upper.contains("Bollinger Upper Band"));

    String lower = IndicatorCalculator.compute(data, "boll_lb", 3, "2024-01-30");
    assertTrue(lower.contains("Bollinger Lower Band"));
  }

  @Test
  void bollingerBandsOrdering() {
    // Upper > Middle > Lower for any data
    List<OhlcvRow> data = syntheticData(30);
    String upper = IndicatorCalculator.compute(data, "boll_ub", 1, "2024-01-30");
    String middle = IndicatorCalculator.compute(data, "boll", 1, "2024-01-30");
    String lower = IndicatorCalculator.compute(data, "boll_lb", 1, "2024-01-30");

    double ubVal = extractLastValue(upper);
    double midVal = extractLastValue(middle);
    double lbVal = extractLastValue(lower);
    assertTrue(ubVal > midVal, "Upper band should be above middle");
    assertTrue(midVal > lbVal, "Middle band should be above lower");
  }

  @Test
  void atrBasic() {
    List<OhlcvRow> data = syntheticData(20);
    String result = IndicatorCalculator.compute(data, "atr", 3, "2024-01-20");
    assertTrue(result.contains("ATR(14)"));
    assertFalse(result.contains("Insufficient"));
  }

  @Test
  void atrInsufficientData() {
    List<OhlcvRow> data = syntheticData(10);
    String result = IndicatorCalculator.compute(data, "atr", 5, "2024-01-10");
    assertTrue(result.contains("Insufficient"));
  }

  @Test
  void vwmaBasic() {
    List<OhlcvRow> data = syntheticData(25);
    String result = IndicatorCalculator.compute(data, "vwma", 3, "2024-01-25");
    assertTrue(result.contains("VWMA(20)"));
    assertFalse(result.contains("Insufficient"));
  }

  @Test
  void mfiBasic() {
    List<OhlcvRow> data = syntheticData(20);
    String result = IndicatorCalculator.compute(data, "mfi", 3, "2024-01-20");
    assertTrue(result.contains("MFI(14)"));
    // All rising typical prices → MFI should be 100
    assertTrue(result.contains("100.00"));
  }

  @Test
  void mfiInsufficientData() {
    List<OhlcvRow> data = syntheticData(10);
    String result = IndicatorCalculator.compute(data, "mfi", 5, "2024-01-10");
    assertTrue(result.contains("Insufficient"));
  }

  @Test
  void computeUnsupported() {
    List<OhlcvRow> data = syntheticData(10);
    String result = IndicatorCalculator.compute(data, "unknown_indicator", 5, "2024-01-10");
    assertTrue(result.contains("Unsupported"));
    assertTrue(result.contains("close_50_sma")); // lists supported
    assertTrue(result.contains("macd")); // new indicators listed
    assertTrue(result.contains("mfi"));
  }

  /** Extract the numeric value from the last data line of an indicator result. */
  private static double extractLastValue(String result) {
    String[] lines = result.strip().split("\n");
    String lastLine = lines[lines.length - 1]; // e.g. "2024-01-30: 25.50"
    return Double.parseDouble(lastLine.substring(lastLine.indexOf(":") + 1).trim());
  }

  @Test
  void lookAheadBiasFilter() {
    List<OhlcvRow> data = syntheticData(10); // dates 2024-01-01 to 2024-01-10
    // Only see data up to 2024-01-05
    String result = IndicatorCalculator.sma(data, 3, 3, "2024-01-05");
    assertFalse(result.contains("2024-01-06"));
    assertFalse(result.contains("2024-01-10"));
  }
}
