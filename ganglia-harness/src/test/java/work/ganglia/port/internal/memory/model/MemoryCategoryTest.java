package work.ganglia.port.internal.memory.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MemoryCategoryTest {

  @Test
  void originalCategories_exist() {
    assertNotNull(MemoryCategory.DECISION);
    assertNotNull(MemoryCategory.BUGFIX);
    assertNotNull(MemoryCategory.REFACTOR);
    assertNotNull(MemoryCategory.FEATURE);
    assertNotNull(MemoryCategory.ENVIRONMENT);
    assertNotNull(MemoryCategory.OBSERVATION);
    assertNotNull(MemoryCategory.UNKNOWN);
  }

  @Test
  void newKnowledgeCategories_exist() {
    assertNotNull(MemoryCategory.USER_PREFERENCE);
    assertNotNull(MemoryCategory.CONVENTION);
    assertNotNull(MemoryCategory.LESSON_LEARNED);
    assertNotNull(MemoryCategory.SKILL_PATTERN);
  }

  @Test
  void totalCategoryCount() {
    assertEquals(11, MemoryCategory.values().length);
  }

  @Test
  void valueOf_roundTrips() {
    for (MemoryCategory cat : MemoryCategory.values()) {
      assertEquals(cat, MemoryCategory.valueOf(cat.name()));
    }
  }
}
