package work.ganglia.trading.data.vendor;

import io.vertx.core.Vertx;

import work.ganglia.trading.config.TradingConfig.DataVendor;
import work.ganglia.trading.data.cache.OhlcvCache;
import work.ganglia.trading.data.vendor.alphavantage.AlphaVantageClient;
import work.ganglia.trading.data.vendor.alphavantage.AlphaVantageFundamentalsVendor;
import work.ganglia.trading.data.vendor.alphavantage.AlphaVantageIndicatorVendor;
import work.ganglia.trading.data.vendor.alphavantage.AlphaVantageNewsVendor;
import work.ganglia.trading.data.vendor.alphavantage.AlphaVantageStockVendor;
import work.ganglia.trading.data.vendor.yfinance.YFinanceClient;
import work.ganglia.trading.data.vendor.yfinance.YFinanceFundamentalsVendor;
import work.ganglia.trading.data.vendor.yfinance.YFinanceIndicatorVendor;
import work.ganglia.trading.data.vendor.yfinance.YFinanceNewsVendor;
import work.ganglia.trading.data.vendor.yfinance.YFinanceStockVendor;

/**
 * Factory for registering data vendor implementations with a {@link VendorRouter}. Extracted from
 * TradingAgentBuilder to improve testability and dependency inversion.
 */
public final class VendorFactory {
  private VendorFactory() {}

  /** Register all Yahoo Finance vendor implementations. */
  public static void registerYFinanceVendors(VendorRouter router, Vertx vertx, OhlcvCache cache) {
    YFinanceClient client = new YFinanceClient(vertx);
    YFinanceStockVendor stock = new YFinanceStockVendor(client, cache);

    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, stock);
    router.register(
        DataVendorSpi.Indicator.class, DataVendor.YFINANCE, new YFinanceIndicatorVendor(stock));
    router.register(
        DataVendorSpi.Fundamentals.class,
        DataVendor.YFINANCE,
        new YFinanceFundamentalsVendor(client));
    router.register(DataVendorSpi.News.class, DataVendor.YFINANCE, new YFinanceNewsVendor(client));
  }

  /** Register all Alpha Vantage vendor implementations. */
  public static void registerAlphaVantageVendors(
      VendorRouter router, Vertx vertx, String apiKey, OhlcvCache cache) {
    AlphaVantageClient client = new AlphaVantageClient(vertx, apiKey);
    AlphaVantageStockVendor stock = new AlphaVantageStockVendor(client, cache);

    router.register(DataVendorSpi.Stock.class, DataVendor.ALPHA_VANTAGE, stock);
    router.register(
        DataVendorSpi.Indicator.class,
        DataVendor.ALPHA_VANTAGE,
        new AlphaVantageIndicatorVendor(client, stock));
    router.register(
        DataVendorSpi.Fundamentals.class,
        DataVendor.ALPHA_VANTAGE,
        new AlphaVantageFundamentalsVendor(client));
    router.register(
        DataVendorSpi.News.class, DataVendor.ALPHA_VANTAGE, new AlphaVantageNewsVendor(client));
  }
}
