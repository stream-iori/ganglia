package work.ganglia.trading.data.vendor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.config.TradingConfig.DataVendor;

class VendorRouterTest {

  private VendorRouter router;

  @BeforeEach
  void setUp() {
    router = new VendorRouter(TradingConfig.defaults());
  }

  @Test
  void primaryVendorSucceeds() {
    DataVendorSpi.Stock primary = (s, sd, ed) -> Future.succeededFuture("primary-data");
    DataVendorSpi.Stock fallback = (s, sd, ed) -> Future.succeededFuture("fallback-data");

    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, primary);
    router.register(DataVendorSpi.Stock.class, DataVendor.ALPHA_VANTAGE, fallback);

    String result =
        router
            .route(
                DataVendorSpi.Stock.class, v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01"))
            .result();
    assertEquals("primary-data", result);
  }

  @Test
  void fallbackOnRateLimit() {
    DataVendorSpi.Stock primary =
        (s, sd, ed) -> Future.failedFuture(new VendorRateLimitException("rate limited"));
    DataVendorSpi.Stock fallback = (s, sd, ed) -> Future.succeededFuture("fallback-data");

    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, primary);
    router.register(DataVendorSpi.Stock.class, DataVendor.ALPHA_VANTAGE, fallback);

    String result =
        router
            .route(
                DataVendorSpi.Stock.class, v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01"))
            .result();
    assertEquals("fallback-data", result);
  }

  @Test
  void nonRateLimitErrorPropagates() {
    DataVendorSpi.Stock primary =
        (s, sd, ed) -> Future.failedFuture(new RuntimeException("network error"));
    DataVendorSpi.Stock fallback = (s, sd, ed) -> Future.succeededFuture("fallback-data");

    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, primary);
    router.register(DataVendorSpi.Stock.class, DataVendor.ALPHA_VANTAGE, fallback);

    Future<String> result =
        router.route(
            DataVendorSpi.Stock.class, v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01"));
    assertTrue(result.failed());
    assertEquals("network error", result.cause().getMessage());
  }

  @Test
  void noVendorRegisteredFails() {
    Future<String> result =
        router.route(
            DataVendorSpi.Stock.class, v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01"));
    assertTrue(result.failed());
  }

  @Test
  void onlyFallbackRegistered() {
    DataVendorSpi.Stock fallback = (s, sd, ed) -> Future.succeededFuture("fallback-only");
    router.register(DataVendorSpi.Stock.class, DataVendor.ALPHA_VANTAGE, fallback);

    String result =
        router
            .route(
                DataVendorSpi.Stock.class, v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01"))
            .result();
    assertEquals("fallback-only", result);
  }
}
