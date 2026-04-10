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

/** ToolSet for fundamental company overview, balance sheet, cash flow, income statement. */
public class FundamentalsTools extends AbstractVendorToolSet {

  public FundamentalsTools(VendorRouter vendorRouter) {
    super(vendorRouter);
  }

  @Override
  public List<ToolDefinition> getDefinitions() {
    return List.of(
        new ToolDefinition(
            "get_fundamentals",
            "Get company fundamentals including profile, financial data, and key statistics.",
            """
                {
                  "type": "object",
                  "properties": {
                    "ticker": { "type": "string", "description": "Stock ticker symbol" },
                    "curr_date": { "type": "string", "description": "Current date for look-ahead bias prevention (YYYY-MM-DD)" }
                  },
                  "required": ["ticker", "curr_date"]
                }
                """),
        new ToolDefinition(
            "get_balance_sheet",
            "Get company balance sheet data (quarterly or annual).",
            """
                {
                  "type": "object",
                  "properties": {
                    "ticker": { "type": "string", "description": "Stock ticker symbol" },
                    "freq": { "type": "string", "description": "Frequency: 'quarterly' or 'annual'", "default": "quarterly" },
                    "curr_date": { "type": "string", "description": "Current date (YYYY-MM-DD)" }
                  },
                  "required": ["ticker", "curr_date"]
                }
                """),
        new ToolDefinition(
            "get_cashflow",
            "Get company cash flow statement data (quarterly or annual).",
            """
                {
                  "type": "object",
                  "properties": {
                    "ticker": { "type": "string", "description": "Stock ticker symbol" },
                    "freq": { "type": "string", "description": "Frequency: 'quarterly' or 'annual'", "default": "quarterly" },
                    "curr_date": { "type": "string", "description": "Current date (YYYY-MM-DD)" }
                  },
                  "required": ["ticker", "curr_date"]
                }
                """),
        new ToolDefinition(
            "get_income_statement",
            "Get company income statement data (quarterly or annual).",
            """
                {
                  "type": "object",
                  "properties": {
                    "ticker": { "type": "string", "description": "Stock ticker symbol" },
                    "freq": { "type": "string", "description": "Frequency: 'quarterly' or 'annual'", "default": "quarterly" },
                    "curr_date": { "type": "string", "description": "Current date (YYYY-MM-DD)" }
                  },
                  "required": ["ticker", "curr_date"]
                }
                """));
  }

  @Override
  public Future<ToolInvokeResult> execute(
      String toolName,
      Map<String, Object> args,
      SessionContext context,
      ExecutionContext executionContext) {
    String ticker = requireString(args, "ticker");
    String currDate = requireString(args, "curr_date");
    String freq = (String) args.getOrDefault("freq", "quarterly");

    if (ticker == null || currDate == null) {
      return missingArgs("ticker", "curr_date");
    }

    String upperTicker = ticker.toUpperCase();

    return switch (toolName) {
      case "get_fundamentals" ->
          routeToVendor(
              DataVendorSpi.Fundamentals.class, v -> v.getFundamentals(upperTicker, currDate));
      case "get_balance_sheet" ->
          routeToVendor(
              DataVendorSpi.Fundamentals.class,
              v -> v.getBalanceSheet(upperTicker, freq, currDate));
      case "get_cashflow" ->
          routeToVendor(
              DataVendorSpi.Fundamentals.class, v -> v.getCashflow(upperTicker, freq, currDate));
      case "get_income_statement" ->
          routeToVendor(
              DataVendorSpi.Fundamentals.class,
              v -> v.getIncomeStatement(upperTicker, freq, currDate));
      default -> Future.succeededFuture(ToolInvokeResult.error("Unknown tool: " + toolName));
    };
  }
}
