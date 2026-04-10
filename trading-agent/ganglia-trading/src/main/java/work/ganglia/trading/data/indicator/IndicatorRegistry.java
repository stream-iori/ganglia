package work.ganglia.trading.data.indicator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/**
 * Registry of {@link TechnicalIndicator} implementations. Replaces the monolithic switch statement
 * in {@link IndicatorCalculator#compute} with an open/extensible lookup.
 */
public class IndicatorRegistry {

  private final Map<String, TechnicalIndicator> indicators = new LinkedHashMap<>();

  /** Register an indicator. Overwrites any existing indicator with the same name. */
  public void register(TechnicalIndicator indicator) {
    indicators.put(indicator.name().toLowerCase(), indicator);
  }

  /** Compute an indicator by name. Returns an error message for unsupported indicators. */
  public String compute(
      List<OhlcvRow> data, String indicatorName, int lookBackDays, String currDate) {
    TechnicalIndicator indicator = indicators.get(indicatorName.toLowerCase());
    if (indicator == null) {
      return "Unsupported indicator: "
          + indicatorName
          + ". Supported: "
          + String.join(", ", supportedNames());
    }
    return indicator.compute(data, lookBackDays, currDate);
  }

  /** Return the set of all registered indicator names. */
  public Set<String> supportedNames() {
    return indicators.keySet();
  }

  /** Create a registry pre-loaded with all built-in indicators. */
  public static IndicatorRegistry createDefault() {
    IndicatorRegistry registry = new IndicatorRegistry();
    registry.register(new SmaIndicator(50, "close_50_sma"));
    registry.register(new SmaIndicator(200, "close_200_sma"));
    registry.register(new EmaIndicator(10, "close_10_ema"));
    registry.register(new RsiIndicator());
    registry.register(new MacdIndicator("macd"));
    registry.register(new MacdIndicator("macds"));
    registry.register(new MacdIndicator("macdh"));
    registry.register(new BollingerBandsIndicator("boll"));
    registry.register(new BollingerBandsIndicator("boll_ub"));
    registry.register(new BollingerBandsIndicator("boll_lb"));
    registry.register(new AtrIndicator());
    registry.register(new VwmaIndicator());
    registry.register(new MfiIndicator());
    return registry;
  }
}
