package work.ganglia.trading.config;

/**
 * Domain-specific configuration for the Trading Agent system.
 *
 * @param investmentStyle The investment style anchor (VALUE, GROWTH, EVENT_DRIVEN)
 * @param maxDebateRounds Maximum rounds for Bull/Bear adversarial debate
 * @param maxRiskDiscussRounds Maximum rounds for Risk Team (Aggressive/Neutral/Conservative) debate
 * @param outputLanguage Language for all agent outputs (e.g., "en", "zh")
 * @param instrumentContext Instrument type context (e.g., "stock", "crypto", "forex")
 * @param dataVendor Primary data vendor (YFINANCE, ALPHA_VANTAGE)
 * @param fallbackVendor Fallback data vendor when primary hits rate limits
 * @param enableMemoryTwr Enable Time-Weighted Retrieval for memory decay
 * @param memoryHalfLifeDays Half-life in days for memory time decay
 * @param dataCacheDir Directory path for OHLCV data cache
 */
public record TradingConfig(
    InvestmentStyle investmentStyle,
    int maxDebateRounds,
    int maxRiskDiscussRounds,
    String outputLanguage,
    String instrumentContext,
    DataVendor dataVendor,
    DataVendor fallbackVendor,
    boolean enableMemoryTwr,
    int memoryHalfLifeDays,
    String dataCacheDir) {

  public enum InvestmentStyle {
    VALUE,
    GROWTH,
    EVENT_DRIVEN
  }

  public enum DataVendor {
    YFINANCE,
    ALPHA_VANTAGE
  }

  public static TradingConfig defaults() {
    return new TradingConfig(
        InvestmentStyle.VALUE,
        3,
        2,
        "en",
        "stock",
        DataVendor.YFINANCE,
        DataVendor.ALPHA_VANTAGE,
        true,
        180,
        ".ganglia/trading-cache");
  }
}
