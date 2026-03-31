package work.ganglia.port.internal.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContextBudgetTest {

  @Test
  void from_128k_window() {
    ContextBudget b = ContextBudget.from(128000, 4096);
    int available = 128000 - 4096; // 123904

    assertEquals(128000, b.contextLimit());
    assertEquals(4096, b.maxGenerationTokens());

    // systemPrompt: 5% of 123904 = 6195 → clamped [1500, 8000] → 6195
    assertEquals((int) (available * 0.05), b.systemPrompt());
    assertTrue(b.systemPrompt() >= 1500 && b.systemPrompt() <= 8000);

    // history: 80% of 123904 = 99123 → clamped [2000, 200000] → 99123
    assertEquals((int) (available * 0.80), b.history());
    assertTrue(b.history() >= 2000 && b.history() <= 200000);

    // currentTurnBudget: 70% of history (99123) = 69386 → clamped [2000, 150000] → 69386
    assertEquals((int) (b.history() * 0.70), b.currentTurnBudget());
    assertTrue(b.currentTurnBudget() >= 2000 && b.currentTurnBudget() <= 150000);

    // toolOutputPerMessage: 4% of 123904 = 4956 → clamped [2000, 16000]
    assertEquals((int) (available * 0.04), b.toolOutputPerMessage());
    assertTrue(b.toolOutputPerMessage() >= 2000 && b.toolOutputPerMessage() <= 16000);

    // observationFallback: 2% of 123904 = 2478 → clamped [1000, 4000]
    assertEquals((int) (available * 0.02), b.observationFallback());
    assertTrue(b.observationFallback() >= 1000 && b.observationFallback() <= 4000);

    // compressionTarget: 50% of 123904 = 61952 → clamped [2000, 250000]
    assertEquals((int) (available * 0.50), b.compressionTarget());
  }

  @Test
  void from_8k_window() {
    ContextBudget b = ContextBudget.from(8000, 1000);
    int available = 7000;

    // systemPrompt: 5% of 7000 = 350 → clamped to min 1500
    assertEquals(1500, b.systemPrompt());
    // history: 80% of 7000 = 5600 → above min 2000
    assertEquals((int) (available * 0.80), b.history());
    // currentTurnBudget: 70% of 5600 = 3920 → above min 2000
    assertEquals((int) (b.history() * 0.70), b.currentTurnBudget());
    // toolOutputPerMessage: 4% of 7000 = 280 → clamped to min 2000
    assertEquals(2000, b.toolOutputPerMessage());
    // observationFallback: 2% of 7000 = 140 → clamped to min 1000
    assertEquals(1000, b.observationFallback());
    // compressionTarget: 50% of 7000 = 3500 → above min 2000
    assertEquals((int) (available * 0.50), b.compressionTarget());
  }

  @Test
  void from_1m_window() {
    ContextBudget b = ContextBudget.from(1000000, 8192);
    int available = 1000000 - 8192;

    // systemPrompt: 5% of 991808 = 49590 → clamped to max 8000
    assertEquals(8000, b.systemPrompt());
    // history: 80% of 991808 = 793446 → clamped to max 200000
    assertEquals(200000, b.history());
    // currentTurnBudget: 70% of 200000 = 140000 → clamped [2000, 150000] → 140000
    assertEquals((int) (200000 * 0.70), b.currentTurnBudget());
    // toolOutputPerMessage: 4% of 991808 = 39672 → clamped to max 16000
    assertEquals(16000, b.toolOutputPerMessage());
    // observationFallback: 2% of 991808 = 19836 → clamped to max 4000
    assertEquals(4000, b.observationFallback());
    // compressionTarget: 50% of 991808 = 495904 → clamped to max 250000
    assertEquals(250000, b.compressionTarget());
  }

  @Test
  void from_zeroGeneration() {
    ContextBudget b = ContextBudget.from(128000, 0);
    assertEquals(128000, b.contextLimit());
    assertEquals(0, b.maxGenerationTokens());
    // available = 128000, allocations should use full contextLimit
    assertEquals((int) (128000 * 0.05), b.systemPrompt());
    assertEquals((int) (128000 * 0.80), b.history());
    assertEquals((int) (b.history() * 0.70), b.currentTurnBudget());
  }

  @Test
  void compressionTarget_derivedCorrectly() {
    ContextBudget b = ContextBudget.from(128000, 4096);
    int available = 128000 - 4096;
    int expectedTarget = (int) (available * 0.50);
    assertEquals(expectedTarget, b.compressionTarget());
  }

  @Test
  void budgetPartsDoNotExceedAvailable() {
    // Test with reasonable window sizes (available must be large enough for clamp minimums)
    for (int ctx : new int[] {32000, 128000, 200000, 1000000}) {
      ContextBudget b = ContextBudget.from(ctx, 4096);
      int available = ctx - 4096;
      int usedParts = b.systemPrompt() + b.toolOutputPerMessage() + b.observationFallback();
      assertTrue(
          usedParts < available,
          "systemPrompt + toolOutput + observationFallback (%d) must be < available (%d) for contextLimit=%d"
              .formatted(usedParts, available, ctx));
    }
  }
}
