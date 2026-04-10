package work.ganglia.port.internal.memory.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NudgeConfigTest {

  @Test
  void defaultConfig_hasExpectedValues() {
    NudgeConfig config = NudgeConfig.DEFAULT;
    assertEquals(10, config.memoryNudgeInterval());
    assertEquals(15, config.skillNudgeInterval());
    assertEquals(6, config.flushMinTurns());
  }

  @Test
  void defaultConfig_allEnabled() {
    NudgeConfig config = NudgeConfig.DEFAULT;
    assertTrue(config.isMemoryNudgeEnabled());
    assertTrue(config.isSkillNudgeEnabled());
    assertTrue(config.isFlushEnabled());
  }

  @Test
  void disabledConfig_zeroValues() {
    NudgeConfig config = new NudgeConfig(0, 0, 0);
    assertFalse(config.isMemoryNudgeEnabled());
    assertFalse(config.isSkillNudgeEnabled());
    assertFalse(config.isFlushEnabled());
  }

  @Test
  void partiallyDisabled() {
    NudgeConfig config = new NudgeConfig(5, 0, 3);
    assertTrue(config.isMemoryNudgeEnabled());
    assertFalse(config.isSkillNudgeEnabled());
    assertTrue(config.isFlushEnabled());
  }

  @Test
  void customValues_preserved() {
    NudgeConfig config = new NudgeConfig(20, 30, 12);
    assertEquals(20, config.memoryNudgeInterval());
    assertEquals(30, config.skillNudgeInterval());
    assertEquals(12, config.flushMinTurns());
  }
}
