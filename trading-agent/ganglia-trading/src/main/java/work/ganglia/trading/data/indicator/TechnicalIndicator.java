package work.ganglia.trading.data.indicator;

import java.util.List;

import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;

/** Strategy interface for computing a single technical indicator from OHLCV data. */
public interface TechnicalIndicator {

  /** Unique name used for dispatch, e.g. "close_50_sma", "rsi", "macd". */
  String name();

  /**
   * Compute the indicator.
   *
   * @param data OHLCV rows sorted by date ascending
   * @param lookBackDays number of result days to return
   * @param currDate max date (inclusive) for look-ahead bias prevention
   * @return formatted string of date-value pairs, or an error message
   */
  String compute(List<OhlcvRow> data, int lookBackDays, String currDate);
}
