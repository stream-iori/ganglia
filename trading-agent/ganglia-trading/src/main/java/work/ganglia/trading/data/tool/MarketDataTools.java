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

/**
 * ToolSet for market stock OHLCV and technical indicators. Delegates to {@link VendorRouter} for
 * vendor selection and fallback.
 */
public class MarketDataTools extends AbstractVendorToolSet {

  public MarketDataTools(VendorRouter vendorRouter) {
    super(vendorRouter);
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "get_stock_data",
            "Get historical OHLCV (Open, High, Low, Close, Volume) stock data for a given symbol and date range.",
            """
                {
                  "type": "object",
                  "properties": {
                    "symbol": { "type": "string", "description": "Stock ticker symbol (e.g. AAPL, MSFT)" },
                    "start_date": { "type": "string", "description": "Start date in YYYY-MM-DD format" },
                    "end_date": { "type": "string", "description": "End date in YYYY-MM-DD format" }
                  },
                  "required": ["symbol", "start_date", "end_date"]
                }
                """),
        new ToolDefinition(
            "get_indicators",
            "Compute technical indicators for a stock. Supported: close_50_sma, close_200_sma, close_10_ema, rsi, macd, macds, macdh, boll, boll_ub, boll_lb, atr, vwma, mfi.",
            """
                {
                  "type": "object",
                  "properties": {
                    "symbol": { "type": "string", "description": "Stock ticker symbol" },
                    "indicator": { "type": "string", "description": "Indicator name: close_50_sma, close_200_sma, close_10_ema, rsi, macd, macds (signal), macdh (histogram), boll (middle), boll_ub (upper), boll_lb (lower), atr, vwma, mfi" },
                    "curr_date": { "type": "string", "description": "Current date for look-ahead bias prevention (YYYY-MM-DD)" },
                    "look_back_days": { "type": "integer", "description": "Number of recent days to return", "default": 30 }
                  },
                  "required": ["symbol", "indicator", "curr_date"]
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
      case "get_stock_data" -> executeGetStockData(args);
      case "get_indicators" -> executeGetIndicators(args);
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }

  private Future<ToolInvokeResult> executeGetStockData(Map<String, Object> args) {
    String symbol = requireString(args, "symbol");
    String startDate = requireString(args, "start_date");
    String endDate = requireString(args, "end_date");

    if (symbol == null || startDate == null || endDate == null) {
      return missingArgs("symbol", "start_date", "end_date");
    }

    return routeToVendor(
        DataVendorSpi.Stock.class, v -> v.getStockData(symbol.toUpperCase(), startDate, endDate));
  }

  private Future<ToolInvokeResult> executeGetIndicators(Map<String, Object> args) {
    String symbol = requireString(args, "symbol");
    String indicator = requireString(args, "indicator");
    String currDate = requireString(args, "curr_date");
    int lookBackDays = optionalInt(args, "look_back_days", 30);

    if (symbol == null || indicator == null || currDate == null) {
      return missingArgs("symbol", "indicator", "curr_date");
    }

    return routeToVendor(
        DataVendorSpi.Indicator.class,
        v -> v.getIndicators(symbol.toUpperCase(), indicator, currDate, lookBackDays));
  }
}
