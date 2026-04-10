package work.ganglia.trading.data.vendor.yfinance;

import java.util.List;

import io.vertx.core.Future;

import work.ganglia.trading.data.indicator.IndicatorCalculator;
import work.ganglia.trading.data.indicator.IndicatorCalculator.OhlcvRow;
import work.ganglia.trading.data.vendor.DataVendorSpi;

/**
 * Yahoo Finance indicator vendor. Fetches OHLCV data via the stock vendor and computes indicators
 * locally using {@link IndicatorCalculator}.
 */
public class YFinanceIndicatorVendor implements DataVendorSpi.Indicator {

  private final DataVendorSpi.Stock stockVendor;

  public YFinanceIndicatorVendor(DataVendorSpi.Stock stockVendor) {
    this.stockVendor = stockVendor;
  }

  @Override
  public Future<String> getIndicators(
      String symbol, String indicator, String currDate, int lookBackDays) {
    // Fetch enough historical data for indicator calculation (5 years back)
    String startDate = adjustStartDate(currDate);
    return stockVendor
        .getStockData(symbol, startDate, currDate)
        .map(
            csv -> {
              List<OhlcvRow> data = IndicatorCalculator.parseCsv(csv);
              return IndicatorCalculator.compute(data, indicator, lookBackDays, currDate);
            });
  }

  private static String adjustStartDate(String currDate) {
    try {
      return java.time.LocalDate.parse(currDate).minusYears(5).toString();
    } catch (Exception e) {
      return "2019-01-01";
    }
  }
}
