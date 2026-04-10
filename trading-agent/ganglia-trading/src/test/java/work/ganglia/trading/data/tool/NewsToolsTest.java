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

class NewsToolsTest {

  private NewsTools tools;

  @BeforeEach
  void setUp() {
    VendorRouter router = new VendorRouter(TradingConfig.defaults());

    DataVendorSpi.News stub =
        new DataVendorSpi.News() {
          @Override
          public Future<String> getNews(String ticker, String startDate, String endDate) {
            return Future.succeededFuture("News for " + ticker + " from " + startDate);
          }

          @Override
          public Future<String> getGlobalNews(String currDate, int lookBackDays, int limit) {
            return Future.succeededFuture(
                "Global news as of " + currDate + ", lookback=" + lookBackDays);
          }
        };
    router.register(DataVendorSpi.News.class, DataVendor.YFINANCE, stub);
    tools = new NewsTools(router);
  }

  @Test
  void definitionsRegistered() {
    assertEquals(2, tools.getDefinitions().size());
  }

  @Test
  void getNewsSuccess() {
    ToolInvokeResult result =
        tools
            .execute(
                "get_news",
                Map.of("ticker", "AAPL", "start_date", "2024-01-01", "end_date", "2024-06-01"),
                null,
                null)
            .result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("News for AAPL"));
  }

  @Test
  void getGlobalNewsWithDefaults() {
    ToolInvokeResult result =
        tools.execute("get_global_news", Map.of("curr_date", "2024-06-01"), null, null).result();
    assertEquals(ToolInvokeResult.Status.SUCCESS, result.status());
    assertTrue(result.output().contains("Global news"));
    assertTrue(result.output().contains("lookback=7")); // default
  }

  @Test
  void getNewsMissingArgs() {
    ToolInvokeResult result =
        tools.execute("get_news", Map.of("ticker", "AAPL"), null, null).result();
    assertEquals(ToolInvokeResult.Status.ERROR, result.status());
  }
}
