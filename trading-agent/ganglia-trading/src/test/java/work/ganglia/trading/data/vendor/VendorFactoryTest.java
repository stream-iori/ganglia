package work.ganglia.trading.data.vendor;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.data.cache.OhlcvCache;

@ExtendWith(VertxExtension.class)
class VendorFactoryTest {

  @Test
  void registerYFinanceVendors_registersAllSpiTypes(Vertx vertx) {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());
    OhlcvCache cache = new OhlcvCache(vertx, "/tmp/test-cache");

    VendorFactory.registerYFinanceVendors(router, vertx, cache);

    // Verify all 4 SPI types are registered by routing through them
    // Stock
    assertDoesNotThrow(
        () ->
            router.route(
                DataVendorSpi.Stock.class,
                v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01")));
    // Indicator
    assertDoesNotThrow(
        () ->
            router.route(
                DataVendorSpi.Indicator.class,
                v -> v.getIndicators("AAPL", "rsi", "2024-06-01", 30)));
    // Fundamentals
    assertDoesNotThrow(
        () ->
            router.route(
                DataVendorSpi.Fundamentals.class, v -> v.getFundamentals("AAPL", "2024-06-01")));
    // News
    assertDoesNotThrow(
        () ->
            router.route(
                DataVendorSpi.News.class, v -> v.getNews("AAPL", "2024-01-01", "2024-06-01")));
  }

  @Test
  void registerAlphaVantageVendors_registersAllSpiTypes(Vertx vertx) {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());
    OhlcvCache cache = new OhlcvCache(vertx, "/tmp/test-cache");

    VendorFactory.registerAlphaVantageVendors(router, vertx, "test-api-key", cache);

    // Since default primary is YFINANCE, AV is registered as fallback
    // We need to also register a primary to test that AV vendors exist
    // Instead, just test with a config where AV is primary
    TradingConfig avConfig =
        new io.vertx.core.json.JsonObject()
            .put("investmentStyle", "VALUE")
            .put("maxDebateRounds", 3)
            .put("maxRiskDiscussRounds", 2)
            .put("outputLanguage", "en")
            .put("instrumentContext", "stock")
            .put("dataVendor", "ALPHA_VANTAGE")
            .put("fallbackVendor", "YFINANCE")
            .put("enableMemoryTwr", true)
            .put("memoryHalfLifeDays", 180)
            .mapTo(TradingConfig.class);

    VendorRouter avRouter = new VendorRouter(avConfig);
    VendorFactory.registerAlphaVantageVendors(avRouter, vertx, "test-api-key", cache);

    // All 4 SPI types should be registered
    assertDoesNotThrow(
        () ->
            avRouter.route(
                DataVendorSpi.Stock.class,
                v -> v.getStockData("AAPL", "2024-01-01", "2024-06-01")));
    assertDoesNotThrow(
        () ->
            avRouter.route(
                DataVendorSpi.Indicator.class,
                v -> v.getIndicators("AAPL", "rsi", "2024-06-01", 30)));
    assertDoesNotThrow(
        () ->
            avRouter.route(
                DataVendorSpi.Fundamentals.class, v -> v.getFundamentals("AAPL", "2024-06-01")));
    assertDoesNotThrow(
        () ->
            avRouter.route(
                DataVendorSpi.News.class, v -> v.getNews("AAPL", "2024-01-01", "2024-06-01")));
  }
}
