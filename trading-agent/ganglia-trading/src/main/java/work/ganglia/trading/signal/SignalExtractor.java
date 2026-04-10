package work.ganglia.trading.signal;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SignalExtractor {
  private static final Logger logger = LoggerFactory.getLogger(SignalExtractor.class);

  public enum Signal {
    BUY,
    OVERWEIGHT,
    HOLD,
    UNDERWEIGHT,
    SELL
  }

  public record TradingSignal(Signal signal, double confidence, String rationale) {}

  private static final Pattern SIGNAL_PATTERN =
      Pattern.compile(
          "(?i)\\bfinal\\s+verdict:\\s*\\**\\s*(BUY|OVERWEIGHT|HOLD|UNDERWEIGHT|SELL)\\b");

  private static final Pattern CONFIDENCE_PATTERN =
      Pattern.compile("(?i)\\bconfidence:\\s*\\**\\s*(\\d+\\.\\d+)");

  private static final Pattern RATIONALE_PATTERN = Pattern.compile("(?i)\\brationale:\\s*(.+)");

  private static final TradingSignal DEFAULT_SIGNAL = new TradingSignal(Signal.HOLD, 0.0, "");

  public TradingSignal extract(String pmOutput) {
    if (pmOutput == null || pmOutput.isBlank()) {
      return DEFAULT_SIGNAL;
    }

    Signal signal = extractSignal(pmOutput);
    double confidence = extractConfidence(pmOutput);
    String rationale = extractRationale(pmOutput);

    logger.debug(
        "Extracted signal={}, confidence={}, rationale='{}'", signal, confidence, rationale);
    return new TradingSignal(signal, confidence, rationale);
  }

  private Signal extractSignal(String text) {
    Matcher matcher = SIGNAL_PATTERN.matcher(text);
    if (matcher.find()) {
      return Signal.valueOf(matcher.group(1).toUpperCase());
    }
    return Signal.HOLD;
  }

  private double extractConfidence(String text) {
    Matcher matcher = CONFIDENCE_PATTERN.matcher(text);
    if (matcher.find()) {
      double value = Double.parseDouble(matcher.group(1));
      return Math.max(0.0, Math.min(1.0, value));
    }
    return 0.0;
  }

  private String extractRationale(String text) {
    Matcher matcher = RATIONALE_PATTERN.matcher(text);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return "";
  }
}
