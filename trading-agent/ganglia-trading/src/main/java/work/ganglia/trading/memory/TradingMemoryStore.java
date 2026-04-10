package work.ganglia.trading.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import work.ganglia.trading.config.TradingConfig;

/**
 * Holds role-isolated {@link FinancialSituationMemory} instances for each trading agent persona.
 * Thread-safe: memories are created on first access.
 */
public class TradingMemoryStore {

  private static final Map<String, String> PERSONA_TO_ROLE =
      Map.of(
          "BULL_RESEARCHER", "bull",
          "BEAR_RESEARCHER", "bear",
          "TRADER", "trader",
          "INVEST_JUDGE", "invest_judge",
          "PORTFOLIO_MANAGER", "portfolio_manager");

  private final ConcurrentHashMap<String, FinancialSituationMemory> memories =
      new ConcurrentHashMap<>();
  private final boolean enableTwr;
  private final int halfLifeDays;

  public TradingMemoryStore(TradingConfig config) {
    this.enableTwr = config.enableMemoryTwr();
    this.halfLifeDays = config.memoryHalfLifeDays();
  }

  /**
   * Get the memory for the given persona. Creates one if it doesn't exist.
   *
   * @param persona the agent persona (e.g. "BULL_RESEARCHER")
   * @return the memory instance, or null if the persona is not a reflective role
   */
  public FinancialSituationMemory forRole(String persona) {
    String role = PERSONA_TO_ROLE.get(persona);
    if (role == null) return null;
    return memories.computeIfAbsent(
        role, r -> new FinancialSituationMemory(r, enableTwr, halfLifeDays));
  }

  /** Check if the given persona has a role-specific memory. */
  public boolean hasRole(String persona) {
    return PERSONA_TO_ROLE.containsKey(persona);
  }
}
