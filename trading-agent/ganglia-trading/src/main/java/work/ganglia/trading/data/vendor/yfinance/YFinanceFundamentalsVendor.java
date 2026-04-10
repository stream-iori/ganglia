package work.ganglia.trading.data.vendor.yfinance;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.vendor.DataVendorSpi;

/**
 * Yahoo Finance fundamentals vendor. Fetches company info, balance sheet, cash flow, and income
 * statement via the quoteSummary API.
 */
public class YFinanceFundamentalsVendor implements DataVendorSpi.Fundamentals {

  private final YFinanceClient client;

  public YFinanceFundamentalsVendor(YFinanceClient client) {
    this.client = client;
  }

  @Override
  public Future<String> getFundamentals(String ticker, String currDate) {
    String modules = "assetProfile,financialData,defaultKeyStatistics";
    return fetchQuoteSummary(ticker, modules).map(json -> formatFundamentals(json, ticker));
  }

  @Override
  public Future<String> getBalanceSheet(String ticker, String freq, String currDate) {
    String module =
        "quarterly".equalsIgnoreCase(freq) ? "balanceSheetHistoryQuarterly" : "balanceSheetHistory";
    return fetchQuoteSummary(ticker, module)
        .map(json -> formatFinancialStatement(json, module, ticker, "Balance Sheet", currDate));
  }

  @Override
  public Future<String> getCashflow(String ticker, String freq, String currDate) {
    String module =
        "quarterly".equalsIgnoreCase(freq)
            ? "cashflowStatementHistoryQuarterly"
            : "cashflowStatementHistory";
    return fetchQuoteSummary(ticker, module)
        .map(
            json ->
                formatFinancialStatement(json, module, ticker, "Cash Flow Statement", currDate));
  }

  @Override
  public Future<String> getIncomeStatement(String ticker, String freq, String currDate) {
    String module =
        "quarterly".equalsIgnoreCase(freq)
            ? "incomeStatementHistoryQuarterly"
            : "incomeStatementHistory";
    return fetchQuoteSummary(ticker, module)
        .map(json -> formatFinancialStatement(json, module, ticker, "Income Statement", currDate));
  }

  private Future<String> fetchQuoteSummary(String ticker, String modules) {
    String uri = "/v10/finance/quoteSummary/" + ticker + "?modules=" + modules;
    return client.get(YFinanceClient.QUOTE_HOST, uri);
  }

  static String formatFundamentals(String json, String ticker) {
    JsonObject root = new JsonObject(json);
    JsonObject quoteSummary = root.getJsonObject("quoteSummary");
    if (quoteSummary == null) return "No fundamentals data for " + ticker;

    JsonArray results = quoteSummary.getJsonArray("result");
    if (results == null || results.isEmpty()) return "No fundamentals data for " + ticker;

    JsonObject result = results.getJsonObject(0);
    StringBuilder sb = new StringBuilder();
    sb.append("=== Fundamentals for ").append(ticker).append(" ===\n\n");

    // Asset Profile
    JsonObject profile = result.getJsonObject("assetProfile");
    if (profile != null) {
      sb.append("--- Company Profile ---\n");
      appendField(sb, "Sector", profile, "sector");
      appendField(sb, "Industry", profile, "industry");
      appendField(sb, "Full-Time Employees", profile, "fullTimeEmployees");
      appendField(sb, "Website", profile, "website");
      String summary = profile.getString("longBusinessSummary");
      if (summary != null) {
        sb.append("Business Summary: ")
            .append(summary.length() > 500 ? summary.substring(0, 500) + "..." : summary)
            .append('\n');
      }
      sb.append('\n');
    }

    // Financial Data
    JsonObject financial = result.getJsonObject("financialData");
    if (financial != null) {
      sb.append("--- Financial Data ---\n");
      appendFmtField(sb, "Current Price", financial, "currentPrice");
      appendFmtField(sb, "Target High Price", financial, "targetHighPrice");
      appendFmtField(sb, "Target Low Price", financial, "targetLowPrice");
      appendFmtField(sb, "Target Mean Price", financial, "targetMeanPrice");
      appendFmtField(sb, "Revenue", financial, "totalRevenue");
      appendFmtField(sb, "EBITDA", financial, "ebitda");
      appendFmtField(sb, "Total Debt", financial, "totalDebt");
      appendFmtField(sb, "Total Cash", financial, "totalCash");
      appendFmtField(sb, "Profit Margins", financial, "profitMargins");
      appendFmtField(sb, "Operating Margins", financial, "operatingMargins");
      appendFmtField(sb, "Return on Equity", financial, "returnOnEquity");
      appendFmtField(sb, "Return on Assets", financial, "returnOnAssets");
      appendFmtField(sb, "Debt to Equity", financial, "debtToEquity");
      appendFmtField(sb, "Current Ratio", financial, "currentRatio");
      appendFmtField(sb, "Free Cash Flow", financial, "freeCashflow");
      sb.append('\n');
    }

    // Key Statistics
    JsonObject stats = result.getJsonObject("defaultKeyStatistics");
    if (stats != null) {
      sb.append("--- Key Statistics ---\n");
      appendFmtField(sb, "Trailing PE", stats, "trailingEps");
      appendFmtField(sb, "Forward PE", stats, "forwardPE");
      appendFmtField(sb, "PEG Ratio", stats, "pegRatio");
      appendFmtField(sb, "Price to Book", stats, "priceToBook");
      appendFmtField(sb, "Beta", stats, "beta");
      appendFmtField(sb, "52-Week High", stats, "fiftyTwoWeekHigh");
      appendFmtField(sb, "52-Week Low", stats, "fiftyTwoWeekLow");
      appendFmtField(sb, "50-Day Average", stats, "fiftyDayAverage");
      appendFmtField(sb, "200-Day Average", stats, "twoHundredDayAverage");
      appendFmtField(sb, "Enterprise Value", stats, "enterpriseValue");
      sb.append('\n');
    }

    return sb.toString();
  }

  static String formatFinancialStatement(
      String json, String module, String ticker, String title, String currDate) {
    JsonObject root = new JsonObject(json);
    JsonObject quoteSummary = root.getJsonObject("quoteSummary");
    if (quoteSummary == null) return "No " + title + " data for " + ticker;

    JsonArray results = quoteSummary.getJsonArray("result");
    if (results == null || results.isEmpty()) return "No " + title + " data for " + ticker;

    JsonObject result = results.getJsonObject(0);
    JsonObject moduleData = result.getJsonObject(module);
    if (moduleData == null) return "No " + title + " data for " + ticker;

    JsonArray statements =
        moduleData.getJsonArray(
            module.contains("balanceSheet")
                ? "balanceSheetStatements"
                : module.contains("cashflow") ? "cashflowStatements" : "incomeStatementHistory");
    if (statements == null || statements.isEmpty()) return "No " + title + " data for " + ticker;

    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(title).append(" for ").append(ticker).append(" ===\n\n");

    for (int i = 0; i < statements.size(); i++) {
      JsonObject stmt = statements.getJsonObject(i);
      JsonObject endDateObj = stmt.getJsonObject("endDate");
      if (endDateObj == null) continue;

      String endDate = endDateObj.getString("fmt", "");
      // Look-ahead bias guard
      if (currDate != null && !currDate.isBlank() && endDate.compareTo(currDate) > 0) continue;

      sb.append("--- Period ending ").append(endDate).append(" ---\n");
      for (String key : stmt.fieldNames()) {
        if ("endDate".equals(key) || "maxAge".equals(key)) continue;
        Object val = stmt.getValue(key);
        if (val instanceof JsonObject valObj) {
          Object raw = valObj.getValue("raw");
          String fmt = valObj.getString("fmt", "");
          if (raw != null) {
            sb.append(key).append(": ").append(fmt.isEmpty() ? raw.toString() : fmt).append('\n');
          }
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static void appendField(StringBuilder sb, String label, JsonObject obj, String key) {
    Object val = obj.getValue(key);
    if (val != null) {
      sb.append(label).append(": ").append(val).append('\n');
    }
  }

  private static void appendFmtField(StringBuilder sb, String label, JsonObject obj, String key) {
    Object val = obj.getValue(key);
    if (val instanceof JsonObject valObj) {
      Object raw = valObj.getValue("raw");
      String fmt = valObj.getString("fmt", "");
      if (raw != null) {
        sb.append(label).append(": ").append(fmt.isEmpty() ? raw.toString() : fmt).append('\n');
      }
    } else if (val instanceof Number) {
      sb.append(label).append(": ").append(val).append('\n');
    }
  }
}
