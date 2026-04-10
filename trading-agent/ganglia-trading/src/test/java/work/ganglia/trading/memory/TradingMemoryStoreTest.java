package work.ganglia.trading.memory;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import work.ganglia.trading.config.TradingConfig;

class TradingMemoryStoreTest {

  @Test
  void knownPersonasReturnMemory() {
    var store = new TradingMemoryStore(TradingConfig.defaults());
    assertNotNull(store.forRole("BULL_RESEARCHER"));
    assertNotNull(store.forRole("BEAR_RESEARCHER"));
    assertNotNull(store.forRole("TRADER"));
    assertNotNull(store.forRole("INVEST_JUDGE"));
    assertNotNull(store.forRole("PORTFOLIO_MANAGER"));
  }

  @Test
  void unknownPersonaReturnsNull() {
    var store = new TradingMemoryStore(TradingConfig.defaults());
    assertNull(store.forRole("MARKET_ANALYST"));
    assertNull(store.forRole("UNKNOWN"));
  }

  @Test
  void samePersonaReturnsSameInstance() {
    var store = new TradingMemoryStore(TradingConfig.defaults());
    var m1 = store.forRole("BULL_RESEARCHER");
    var m2 = store.forRole("BULL_RESEARCHER");
    assertSame(m1, m2);
  }

  @Test
  void differentPersonasReturnDifferentInstances() {
    var store = new TradingMemoryStore(TradingConfig.defaults());
    var bull = store.forRole("BULL_RESEARCHER");
    var bear = store.forRole("BEAR_RESEARCHER");
    assertNotSame(bull, bear);
  }

  @Test
  void hasRoleCheck() {
    var store = new TradingMemoryStore(TradingConfig.defaults());
    assertTrue(store.hasRole("TRADER"));
    assertFalse(store.hasRole("NEWS_ANALYST"));
  }
}
