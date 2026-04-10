package work.ganglia.trading.data.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;

import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.trading.config.TradingConfig;
import work.ganglia.trading.config.TradingConfig.DataVendor;
import work.ganglia.trading.data.vendor.DataVendorSpi;
import work.ganglia.trading.data.vendor.VendorRouter;

class FundamentalsToolsTest {

  private FundamentalsTools tools;

  @BeforeEach
  void setUp() {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());

    DataVendorSpi.Fundamentals stub =
        new DataVendorSpi.Fundamentals() {
          @Override
          public Future<String> getFundamentals(String ticker, String currDate) {
            return Future.succeededFuture("Fundamentals for " + ticker);
          }

          @Override
          public Future<String> getBalanceSheet(String ticker, String freq, String currDate) {
            return Future.succeededFuture("Balance Sheet " + freq + " for " + ticker);
          }

          @Override
          public Future<String> getCashflow(String ticker, String freq, String currDate) {
            return Future.succeededFuture("Cash Flow " + freq + " for " + ticker);
          }

          @Override
          public Future<String> getIncomeStatement(String ticker, String freq, String currDate) {
            return Future.succeededFuture("Income Statement " + freq + " for " + ticker);
          }
        };
    router.register(DataVendorSpi.Fundamentals.class, DataVendor.YFINANCE, stub);
    tools = new FundamentalsTools(router);
  }

  @Test
  void definitionsRegistered() {
    assertEquals(4, tools.getDefinitions().size());
  }

  @Test
  void getFundamentalsSuccess() {
    ToolInvokeResult result =
        tools
            .execute(
                "get_fundamentals", Map.of("ticker", "AAPL", "curr_date", "2024-06-01"), null, null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("Fundamentals for AAPL"));
  }

  @Test
  void getBalanceSheetQuarterly() {
    ToolInvokeResult result =
        tools
            .execute(
                "get_balance_sheet",
                Map.of("ticker", "MSFT", "freq", "quarterly", "curr_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("Balance Sheet quarterly for MSFT"));
  }

  @Test
  void missingArgsReturnsError() {
    ToolInvokeResult result =
        tools.execute("get_fundamentals", Map.of("ticker", "AAPL"), null, null).result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
    assertTrue(result.output().contains("Missing"));
  }
}
