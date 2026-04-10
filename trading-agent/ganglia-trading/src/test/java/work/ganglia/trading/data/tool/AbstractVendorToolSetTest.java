package work.ganglia.trading.data.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.config.TradingConfig.DataVendor;
import work.ganglia.trading.data.vendor.DataVendorSpi;
import work.ganglia.trading.data.vendor.VendorRouter;

class AbstractVendorToolSetTest {

  @Test
  void routeToVendorSuccess() {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());
    DataVendorSpi.Stock stub = (s, start, end) -> Future.succeededFuture("OK");
    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, stub);

    // Use MarketDataTools (extends AbstractVendorToolSet) as a concrete test subject
    MarketDataTools tools = new MarketDataTools(router);
    ToolInvokeResult result =
        tools
            .execute(
                "get_stock_data",
                Map.of("symbol", "AAPL", "start_date", "2024-01-01", "end_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertEquals("OK", result.output());
  }

  @Test
  void routeToVendorFailure() {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());
    DataVendorSpi.Stock stub =
        (s, start, end) -> Future.failedFuture(new RuntimeException("vendor down"));
    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, stub);

    MarketDataTools tools = new MarketDataTools(router);
    ToolInvokeResult result =
        tools
            .execute(
                "get_stock_data",
                Map.of("symbol", "AAPL", "start_date", "2024-01-01", "end_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("vendor down"));
  }

  @Test
  void requireStringReturnsValue() {
    Map<String, Object> args = Map.of("key", "value");
    assertEquals("value", AbstractVendorToolSet.requireString(args, "key"));
  }

  @Test
  void requireStringReturnsNullForMissing() {
    Map<String, Object> args = Map.of();
    assertNull(AbstractVendorToolSet.requireString(args, "key"));
  }

  @Test
  void optionalIntReturnsValueIfPresent() {
    Map<String, Object> args = Map.of("count", 42);
    assertEquals(42, AbstractVendorToolSet.optionalInt(args, "count", 10));
  }

  @Test
  void optionalIntReturnsDefaultIfMissing() {
    Map<String, Object> args = Map.of();
    assertEquals(10, AbstractVendorToolSet.optionalInt(args, "count", 10));
  }
}
