package work.ganglia.trading.data.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.config.TradingConfig.DataVendor;
import work.ganglia.trading.data.vendor.DataVendorSpi;
import work.ganglia.trading.data.vendor.VendorRouter;

class MarketDataToolsTest {

  private MarketDataTools tools;

  @BeforeEach
  void setUp() {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());

    // Register stub stock vendor
    DataVendorSpi.Stock stubStock =
        (symbol, start, end) ->
            Future.succeededFuture(
                "Date,Open,High,Low,Close,Volume\n2024-01-02,100,105,99,104,1000000\n");
    router.register(DataVendorSpi.Stock.class, DataVendor.YFINANCE, stubStock);

    // Register stub indicator vendor
    DataVendorSpi.Indicator stubIndicator =
        (symbol, indicator, currDate, lookBack) ->
            Future.succeededFuture("SMA(50): 2024-01-02: 100.00");
    router.register(DataVendorSpi.Indicator.class, DataVendor.YFINANCE, stubIndicator);

    tools = new MarketDataTools(router);
  }

  @Test
  void definitionsRegistered() {
    assertEquals(2, tools.getDefinitions().size());
    Set<String> names = Set.of("get_stock_data", "get_indicators");
    for (ToolDefinition def : tools.getDefinitions()) {
      assertTrue(names.contains(def.name()));
    }
  }

  @Test
  void getStockDataSuccess() {
    ToolInvokeResult result =
        tools
            .execute(
                "get_stock_data",
                Map.of("symbol", "AAPL", "start_date", "2024-01-01", "end_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("Date,Open,High,Low,Close,Volume"));
  }

  @Test
  void getStockDataMissingArgs() {
    ToolInvokeResult result =
        tools.execute("get_stock_data", Map.of("symbol", "AAPL"), null, null).result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("Missing"));
  }

  @Test
  void getIndicatorsSuccess() {
    ToolInvokeResult result =
        tools
            .execute(
                "get_indicators",
                Map.of(
                    "symbol", "AAPL",
                    "indicator", "close_50_sma",
                    "curr_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("SMA(50)"));
  }

  @Test
  void unknownToolReturnsError() {
    ToolInvokeResult result = tools.execute("unknown_tool", Map.of(), null, null).result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
  }
}
