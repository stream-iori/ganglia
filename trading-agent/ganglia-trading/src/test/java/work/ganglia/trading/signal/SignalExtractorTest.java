package work.ganglia.trading.signal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import work.ganglia.trading.signal.SignalExtractor.Signal;
import work.ganglia.trading.signal.SignalExtractor.TradingSignal;

class SignalExtractorTest {

  private SignalExtractor extractor;

  @BeforeEach
  void setUp() {
    extractor = new SignalExtractor();
  }

  @Nested
  class ValidSignalParsing {

    @Test
    void extractsBuySignal() {
      String input =
          """
          Based on my analysis, the risk-adjusted outlook supports a positive position.

          **Final Verdict: BUY**
          **Confidence: 0.85**

          Rationale: Strong momentum confirmed by fundamental strength and positive sentiment.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.BUY, result.signal());
      assertEquals(
          "Strong momentum confirmed by fundamental strength and positive sentiment.",
          result.rationale());
    }

    @Test
    void extractsSellSignal() {
      String input =
          """
          The outlook is bearish across all dimensions.

          **Final Verdict: SELL**
          **Confidence: 0.92**

          Rationale: Deteriorating fundamentals with negative momentum.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.SELL, result.signal());
      assertEquals(0.92, result.confidence(), 0.001);
    }

    @Test
    void extractsHoldSignal() {
      String input =
          """
          Mixed signals across indicators.

          **Final Verdict: HOLD**
          **Confidence: 0.50**

          Rationale: Conflicting momentum and fundamental signals.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.HOLD, result.signal());
      assertEquals(0.50, result.confidence(), 0.001);
    }

    @Test
    void extractsOverweightSignal() {
      String input =
          """
          Moderately bullish outlook.

          **Final Verdict: OVERWEIGHT**
          **Confidence: 0.72**

          Rationale: Positive trend with some headwinds.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.OVERWEIGHT, result.signal());
    }

    @Test
    void extractsUnderweightSignal() {
      String input =
          """
          Slightly bearish sentiment prevails.

          **Final Verdict: UNDERWEIGHT**
          **Confidence: 0.65**

          Rationale: Weak technicals despite stable fundamentals.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.UNDERWEIGHT, result.signal());
    }

    @Test
    void extractsConfidenceScore() {
      String input =
          """
          **Final Verdict: BUY**
          **Confidence: 0.77**

          Rationale: All signals align.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(0.77, result.confidence(), 0.001);
    }

    @Test
    void parsesSignalCaseInsensitively() {
      String input =
          """
          **Final Verdict: buy**
          **confidence: 0.60**

          Rationale: Lowercase test.
          """;

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.BUY, result.signal());
      assertEquals(0.60, result.confidence(), 0.001);
    }
  }

  @Nested
  class MalformedInput {

    @Test
    void returnsHoldWithLowConfidence_whenNoSignalFound() {
      String input = "This analysis does not contain any trading signal or verdict.";

      TradingSignal result = extractor.extract(input);

      assertEquals(Signal.HOLD, result.signal());
      assertEquals(0.0, result.confidence(), 0.001);
    }

    @Test
    void handlesEmptyInput() {
      TradingSignal result = extractor.extract("");

      assertEquals(Signal.HOLD, result.signal());
      assertEquals(0.0, result.confidence(), 0.001);
      assertTrue(result.rationale().isEmpty());
    }

    @Test
    void handlesNullInput() {
      TradingSignal result = extractor.extract(null);

      assertEquals(Signal.HOLD, result.signal());
      assertEquals(0.0, result.confidence(), 0.001);
    }
  }
}
