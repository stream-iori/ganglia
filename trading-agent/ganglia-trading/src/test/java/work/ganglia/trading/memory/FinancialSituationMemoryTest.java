package work.ganglia.trading.memory;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class FinancialSituationMemoryTest {

  @Test
  void emptyMemoryReturnsNoMatches() {
    var memory = new FinancialSituationMemory("bull", false, 180);
    List<FinancialSituationMemory.MemoryMatch> results = memory.retrieve("any query", 5);
    assertTrue(results.isEmpty());
  }

  @Test
  void addAndRetrieveSingleEntry() {
    var memory = new FinancialSituationMemory("bull", false, 180);
    memory.addSituation(
        "Apple stock rising on strong iPhone sales and services revenue growth",
        "Consider buying on pullback, services momentum is sustainable");
    List<FinancialSituationMemory.MemoryMatch> results =
        memory.retrieve("Apple iPhone sales growth", 1);
    assertEquals(1, results.size());
    assertTrue(results.get(0).score() > 0);
    assertTrue(results.get(0).advice().contains("Consider buying"));
  }

  @Test
  void topKRanking() {
    var memory = new FinancialSituationMemory("bear", false, 180);
    memory.addSituation("tech sector crash semiconductor shortage", "reduce exposure to hardware");
    memory.addSituation("oil prices rising inflation concerns", "hedge with commodities");
    memory.addSituation(
        "tech earnings beat expectations semiconductor demand surge",
        "increase semiconductor position");

    List<FinancialSituationMemory.MemoryMatch> results =
        memory.retrieve("semiconductor tech earnings", 2);
    assertEquals(2, results.size());
    // Both results should be tech-related, not oil
    assertTrue(
        results.get(0).score() >= results.get(1).score(), "Results should be ranked by score");
    assertFalse(results.get(0).advice().contains("commodities"));
  }

  @Test
  void topKLimitedByAvailable() {
    var memory = new FinancialSituationMemory("trader", false, 180);
    memory.addSituation("market situation one", "advice one");
    List<FinancialSituationMemory.MemoryMatch> results = memory.retrieve("situation", 5);
    assertEquals(1, results.size());
  }

  @Test
  void noMatchingTermsReturnsEmpty() {
    var memory = new FinancialSituationMemory("judge", false, 180);
    memory.addSituation("apple stock price", "buy apple");
    List<FinancialSituationMemory.MemoryMatch> results = memory.retrieve("bitcoin crypto", 5);
    assertTrue(results.isEmpty());
  }

  @Test
  void sizeTracksEntries() {
    var memory = new FinancialSituationMemory("pm", false, 180);
    assertEquals(0, memory.size());
    memory.addSituation("s1", "a1");
    assertEquals(1, memory.size());
    memory.addSituation("s2", "a2");
    assertEquals(2, memory.size());
  }

  @Test
  void roleNameAccessor() {
    var memory = new FinancialSituationMemory("bull", false, 180);
    assertEquals("bull", memory.roleName());
  }
}
