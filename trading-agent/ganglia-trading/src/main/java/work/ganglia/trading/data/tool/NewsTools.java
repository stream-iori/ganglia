package work.ganglia.trading.data.tool;

import java.util.List;
import java.util.Map;

import io.vertx.core.Future;

import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.trading.data.vendor.DataVendorSpi;
import work.ganglia.trading.data.vendor.VendorRouter;

/** ToolSet for news data: ticker-specific news and global market news. */
public class NewsTools extends AbstractVendorToolSet {

  public NewsTools(VendorRouter vendorRouter) {
    super(vendorRouter);
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "get_news",
            "Get recent news articles for a specific stock ticker.",
            """
                {
                  "type": "object",
                  "properties": {
                    "ticker": { "type": "string", "description": "Stock ticker symbol" },
                    "start_date": { "type": "string", "description": "Start date (YYYY-MM-DD)" },
                    "end_date": { "type": "string", "description": "End date (YYYY-MM-DD)" }
                  },
                  "required": ["ticker", "start_date", "end_date"]
                }
                """),
        new ToolDefinition(
            "get_global_news",
            "Get global market and economic news articles.",
            """
                {
                  "type": "object",
                  "properties": {
                    "curr_date": { "type": "string", "description": "Current date (YYYY-MM-DD)" },
                    "look_back_days": { "type": "integer", "description": "Number of days to look back", "default": 7 },
                    "limit": { "type": "integer", "description": "Maximum number of articles", "default": 20 }
                  },
                  "required": ["curr_date"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    return switch (toolName) {
      case "get_news" -> executeGetNews(args);
      case "get_global_news" -> executeGetGlobalNews(args);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  private Future<ToolInvokeResult> executeGetNews(Map<String, Object> args) {
    String ticker = requireString(args, "ticker");
    String startDate = requireString(args, "start_date");
    String endDate = requireString(args, "end_date");

    if (ticker == null || startDate == null || endDate == null) {
      return missingArgs("ticker", "start_date", "end_date");
    }

    return routeToVendor(
        DataVendorSpi.News.class, v -> v.getNews(ticker.toUpperCase(), startDate, endDate));
  }

  private Future<ToolInvokeResult> executeGetGlobalNews(Map<String, Object> args) {
    String currDate = requireString(args, "curr_date");
    int lookBackDays = optionalInt(args, "look_back_days", 7);
    int limit = optionalInt(args, "limit", 20);

    if (currDate == null) {
      return missingArgs("curr_date");
    }

    return routeToVendor(
        DataVendorSpi.News.class, v -> v.getGlobalNews(currDate, lookBackDays, limit));
  }
}
