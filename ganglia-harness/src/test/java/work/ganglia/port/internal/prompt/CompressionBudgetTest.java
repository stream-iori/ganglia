package work.ganglia.port.internal.prompt;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CompressionBudgetTest {

  @Test
  void defaults_allValuesPositive() {
    CompressionBudget b = CompressionBudget.defaults();
    assertTrue(b.chunkingThreshold() > 0 && b.chunkingThreshold() < 1.0);
    assertTrue(b.chunkSize() > 0 && b.chunkSize() < 1.0);
    assertTrue(b.chunkSize() < b.chunkingThreshold(), "chunkSize must be smaller than threshold");
    assertTrue(b.reflectMaxTokens() > 0);
    assertTrue(b.compressMaxTokens() > 0);
    assertTrue(b.summaryTokenLimit() > 0);
    assertTrue(b.observationCompressMaxTokens() > 0);
  }

  @Test
  void defaults_specificValues() {
    CompressionBudget b = CompressionBudget.defaults();
    assertEquals(0.8, b.chunkingThreshold());
    assertEquals(0.6, b.chunkSize());
    assertEquals(1024, b.reflectMaxTokens());
    assertEquals(2048, b.compressMaxTokens());
    assertEquals(1500, b.summaryTokenLimit());
    assertEquals(1024, b.observationCompressMaxTokens());
  }

  @Test
  void customValues_overrideDefaults() {
    CompressionBudget custom = new CompressionBudget(0.9, 0.7, 512, 4096, 2000, 2048);
    assertEquals(0.9, custom.chunkingThreshold());
    assertEquals(0.7, custom.chunkSize());
    assertEquals(512, custom.reflectMaxTokens());
    assertEquals(4096, custom.compressMaxTokens());
    assertEquals(2000, custom.summaryTokenLimit());
    assertEquals(2048, custom.observationCompressMaxTokens());
  }
}
