package work.ganglia.trading.data.indicator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

class IndicatorRegistryTest {

  private IndicatorRegistry registry;

  private static List<OhlcvRow> syntheticData(int n) {
    return IntStream.range(0, n)
        .mapToObj(
            i -> {
              String date = String.format("2024-01-%02d", i + 1);
              double close = 10.0 + i;
              return new OhlcvRow(date, close, close + 1, close - 1, close, 1000L * (i + 1));
            })
        .toList();
  }

  @BeforeEach
  void setUp() {
    registry = IndicatorRegistry.createDefault();
  }

  @Test
  void defaultRegistryContainsAllIndicators() {
    var names = registry.supportedNames();
    assertTrue(names.contains("close_50_sma"));
    assertTrue(names.contains("close_200_sma"));
    assertTrue(names.contains("close_10_ema"));
    assertTrue(names.contains("rsi"));
    assertTrue(names.contains("macd"));
    assertTrue(names.contains("macds"));
    assertTrue(names.contains("macdh"));
    assertTrue(names.contains("boll"));
    assertTrue(names.contains("boll_ub"));
    assertTrue(names.contains("boll_lb"));
    assertTrue(names.contains("atr"));
    assertTrue(names.contains("vwma"));
    assertTrue(names.contains("mfi"));
    assertEquals(13, names.size());
  }

  @Test
  void computeDispatchesToCorrectIndicator() {
    List<OhlcvRow> data = syntheticData(60);
    assertTrue(registry.compute(data, "close_50_sma", 5, "2024-02-29").contains("SMA(50)"));
    assertTrue(registry.compute(data, "close_10_ema", 5, "2024-02-29").contains("EMA(10)"));
    assertTrue(registry.compute(data, "rsi", 5, "2024-02-29").contains("RSI(14)"));
    assertTrue(registry.compute(data, "macd", 3, "2024-02-29").contains("MACD"));
    assertTrue(registry.compute(data, "boll", 3, "2024-02-29").contains("Bollinger Middle Band"));
    assertTrue(registry.compute(data, "atr", 3, "2024-02-29").contains("ATR(14)"));
    assertTrue(registry.compute(data, "vwma", 3, "2024-02-29").contains("VWMA(20)"));
    assertTrue(registry.compute(data, "mfi", 3, "2024-02-29").contains("MFI(14)"));
  }

  @Test
  void unsupportedIndicatorReturnsError() {
    List<OhlcvRow> data = syntheticData(10);
    String result = registry.compute(data, "unknown_indicator", 5, "2024-01-10");
    assertTrue(result.contains("Unsupported"));
    assertTrue(result.contains("close_50_sma"));
    assertTrue(result.contains("mfi"));
  }

  @Test
  void caseInsensitiveDispatch() {
    List<OhlcvRow> data = syntheticData(60);
    assertTrue(registry.compute(data, "CLOSE_50_SMA", 5, "2024-02-29").contains("SMA(50)"));
    assertTrue(registry.compute(data, "RSI", 5, "2024-02-29").contains("RSI(14)"));
  }

  @Test
  void customIndicatorCanBeRegistered() {
    registry.register(
        new TechnicalIndicator() {
          @Override
          public String name() {
            return "custom";
          }

          @Override
          public String compute(List<OhlcvRow> data, int lookBackDays, String currDate) {
            return "custom result";
          }
        });
    assertTrue(registry.supportedNames().contains("custom"));
    assertEquals("custom result", registry.compute(List.of(), "custom", 5, null));
  }

  @Test
  void registryProducesSameOutputAsOriginal() {
    List<OhlcvRow> data = syntheticData(60);
    String currDate = "2024-02-29";

    assertEquals(
        IndicatorCalculator.compute(data, "close_50_sma", 5, currDate),
        registry.compute(data, "close_50_sma", 5, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "rsi", 5, currDate),
        registry.compute(data, "rsi", 5, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "macd", 3, currDate),
        registry.compute(data, "macd", 3, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "boll_ub", 3, currDate),
        registry.compute(data, "boll_ub", 3, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "atr", 3, currDate),
        registry.compute(data, "atr", 3, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "vwma", 3, currDate),
        registry.compute(data, "vwma", 3, currDate));

    assertEquals(
        IndicatorCalculator.compute(data, "mfi", 3, currDate),
        registry.compute(data, "mfi", 3, currDate));
  }
}
