package work.ganglia.trading.data.vendor.alphavantage;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import work.ganglia.trading.data.vendor.DataVendorSpi;

/** Alpha Vantage fundamentals vendor using OVERVIEW, BALANCE_SHEET, CASH_FLOW, INCOME_STATEMENT. */
public class AlphaVantageFundamentalsVendor implements DataVendorSpi.Fundamentals {

  private final AlphaVantageClient client;

  public AlphaVantageFundamentalsVendor(AlphaVantageClient client) {
    this.client = client;
  }

  @Override
  public Future<String> getFundamentals(String ticker, String currDate) {
    return client.query("OVERVIEW", "symbol=" + ticker).map(json -> formatOverview(json, ticker));
  }

  @Override
  public Future<String> getBalanceSheet(String ticker, String freq, String currDate) {
    return client
        .query("BALANCE_SHEET", "symbol=" + ticker)
        .map(
            json ->
                formatStatement(
                    json,
                    ticker,
                    "Balance Sheet",
                    "quarterly".equalsIgnoreCase(freq) ? "quarterlyReports" : "annualReports",
                    currDate));
  }

  @Override
  public Future<String> getCashflow(String ticker, String freq, String currDate) {
    return client
        .query("CASH_FLOW", "symbol=" + ticker)
        .map(
            json ->
                formatStatement(
                    json,
                    ticker,
                    "Cash Flow",
                    "quarterly".equalsIgnoreCase(freq) ? "quarterlyReports" : "annualReports",
                    currDate));
  }

  @Override
  public Future<String> getIncomeStatement(String ticker, String freq, String currDate) {
    return client
        .query("INCOME_STATEMENT", "symbol=" + ticker)
        .map(
            json ->
                formatStatement(
                    json,
                    ticker,
                    "Income Statement",
                    "quarterly".equalsIgnoreCase(freq) ? "quarterlyReports" : "annualReports",
                    currDate));
  }

  static String formatOverview(String json, String ticker) {
    JsonObject data = new JsonObject(json);
    if (data.isEmpty() || data.containsKey("Error Message")) {
      return "No fundamentals data for " + ticker;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== Company Overview: ").append(ticker).append(" ===\n\n");

    appendIfPresent(sb, "Name", data, "Name");
    appendIfPresent(sb, "Description", data, "Description");
    appendIfPresent(sb, "Sector", data, "Sector");
    appendIfPresent(sb, "Industry", data, "Industry");
    appendIfPresent(sb, "Market Cap", data, "MarketCapitalization");
    appendIfPresent(sb, "PE Ratio", data, "PERatio");
    appendIfPresent(sb, "PEG Ratio", data, "PEGRatio");
    appendIfPresent(sb, "Book Value", data, "BookValue");
    appendIfPresent(sb, "Dividend Yield", data, "DividendYield");
    appendIfPresent(sb, "EPS", data, "EPS");
    appendIfPresent(sb, "Revenue (TTM)", data, "RevenueTTM");
    appendIfPresent(sb, "Gross Profit (TTM)", data, "GrossProfitTTM");
    appendIfPresent(sb, "EBITDA", data, "EBITDA");
    appendIfPresent(sb, "Profit Margin", data, "ProfitMargin");
    appendIfPresent(sb, "Operating Margin", data, "OperatingMarginTTM");
    appendIfPresent(sb, "Return on Assets", data, "ReturnOnAssetsTTM");
    appendIfPresent(sb, "Return on Equity", data, "ReturnOnEquityTTM");
    appendIfPresent(sb, "Beta", data, "Beta");
    appendIfPresent(sb, "52-Week High", data, "52WeekHigh");
    appendIfPresent(sb, "52-Week Low", data, "52WeekLow");
    appendIfPresent(sb, "50-Day Moving Avg", data, "50DayMovingAverage");
    appendIfPresent(sb, "200-Day Moving Avg", data, "200DayMovingAverage");

    return sb.toString();
  }

  static String formatStatement(
      String json, String ticker, String title, String reportsKey, String currDate) {
    JsonObject root = new JsonObject(json);
    JsonArray reports = root.getJsonArray(reportsKey);
    if (reports == null || reports.isEmpty()) {
      return "No " + title + " data for " + ticker;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("=== ").append(title).append(" for ").append(ticker).append(" ===\n\n");

    for (int i = 0; i < reports.size(); i++) {
      JsonObject report = reports.getJsonObject(i);
      String fiscalDate = report.getString("fiscalDateEnding", "");

      // Look-ahead bias guard
      if (currDate != null && !currDate.isBlank() && fiscalDate.compareTo(currDate) > 0) continue;

      sb.append("--- Period ending ").append(fiscalDate).append(" ---\n");
      for (String key : report.fieldNames()) {
        if ("fiscalDateEnding".equals(key) || "reportedCurrency".equals(key)) continue;
        String val = report.getString(key, "");
        if (!"None".equals(val) && !val.isEmpty()) {
          sb.append(key).append(": ").append(val).append('\n');
        }
      }
      sb.append('\n');
    }
    return sb.toString();
  }

  private static void appendIfPresent(StringBuilder sb, String label, JsonObject data, String key) {
    String val = data.getString(key);
    if (val != null && !"None".equals(val) && !val.isEmpty()) {
      sb.append(label).append(": ").append(val).append('\n');
    }
  }
}
