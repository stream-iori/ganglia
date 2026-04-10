package work.ganglia.trading.data.vendor;

import io.vertx.core.Future;

/** Service Provider Interface for data vendor implementations. */
public interface DataVendorSpi {

  /** Stock OHLCV data provider. */
  interface Stock extends DataVendorSpi {
    Future<String> getStockData(String symbol, String startDate, String endDate);
  }

  /** Technical indicator provider. */
  interface Indicator extends DataVendorSpi {
    Future<String> getIndicators(
        String symbol, String indicator, String currDate, int lookBackDays);
  }

  /** Fundamental data provider. */
  interface Fundamentals extends DataVendorSpi {
    Future<String> getFundamentals(String ticker, String currDate);

    Future<String> getBalanceSheet(String ticker, String freq, String currDate);

    Future<String> getCashflow(String ticker, String freq, String currDate);

    Future<String> getIncomeStatement(String ticker, String freq, String currDate);
  }

  /** News data provider. */
  interface News extends DataVendorSpi {
    Future<String> getNews(String ticker, String startDate, String endDate);

    Future<String> getGlobalNews(String currDate, int lookBackDays, int limit);
  }

  /**
   * Social sentiment data provider.
   *
   * <p>TODO: Implement with real data sources — candidates: Reddit API (free), StockTwits API
   * (free), or Alpha Vantage NEWS_SENTIMENT endpoint (already have client). Until then, the Social
   * Analyst uses {@link News} tools with a sentiment-focused prompt to extract signals from news
   * text.
   */
  interface Sentiment extends DataVendorSpi {
    Future<String> getSentiment(String ticker, String currDate, int lookBackDays);

    Future<String> getSocialMentions(String ticker, String currDate, int lookBackDays, int limit);
  }
}
